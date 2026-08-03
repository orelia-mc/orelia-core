package rpg.dungeon.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import rpg.api.StatusApi;
import rpg.dungeon.manager.DungeonInstanceManager;
import rpg.dungeon.model.DungeonArena;
import rpg.dungeon.model.DungeonData;
import rpg.dungeon.model.DungeonEndReason;
import rpg.dungeon.model.DungeonInstance;
import rpg.dungeon.model.DungeonInstanceStatus;
import rpg.dungeon.repository.DungeonRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Starts/completes dungeon runs: party-size validation against {@link DungeonData}, entry
 * teleport, and reward distribution on completion. Rewards go through orelia-core's
 * {@link StatusApi} (EXP) and Vault's {@link Economy} (money) - orelia-world never touches
 * orelia-core's internal status/economy classes directly.
 */
public final class DungeonService {

    public enum StartFailure {
        UNKNOWN_DUNGEON, PARTY_TOO_SMALL, PARTY_TOO_LARGE, WORLD_NOT_FOUND, ALREADY_IN_DUNGEON, DUNGEON_FULL
    }

    private final DungeonRepository repository;
    private final DungeonInstanceManager instanceManager;
    private final StatusApi statusApi;
    private final Economy economy;

    public DungeonService(DungeonRepository repository, DungeonInstanceManager instanceManager,
                           StatusApi statusApi, Economy economy) {
        this.repository = repository;
        this.instanceManager = instanceManager;
        this.statusApi = statusApi;
        this.economy = economy;
    }

    /**
     * Reserves an arena and registers the instance, capturing each member's current location
     * as their return point - but does NOT teleport them in yet, so a challenge can show a
     * pre-entry countdown (see {@code DungeonEncounterService#challenge}) while still rejecting
     * a 4th concurrent party immediately via {@link StartFailure#DUNGEON_FULL}. Call
     * {@link #teleportIn} once the countdown elapses.
     */
    public Optional<StartFailure> start(String dungeonId, List<Player> party) {
        DungeonData data = repository.findById(dungeonId).orElse(null);
        if (data == null) {
            return Optional.of(StartFailure.UNKNOWN_DUNGEON);
        }
        if (party.size() < data.getMinPartySize()) {
            return Optional.of(StartFailure.PARTY_TOO_SMALL);
        }
        if (party.size() > data.getMaxPartySize()) {
            return Optional.of(StartFailure.PARTY_TOO_LARGE);
        }
        for (Player player : party) {
            if (instanceManager.getByPlayer(player.getUniqueId()).isPresent()) {
                return Optional.of(StartFailure.ALREADY_IN_DUNGEON);
            }
        }

        List<DungeonArena> arenas = data.getArenas();
        Optional<Integer> arenaIndex = instanceManager.tryAcquireArena(dungeonId, arenas.size());
        if (arenaIndex.isEmpty()) {
            return Optional.of(StartFailure.DUNGEON_FULL);
        }
        DungeonArena arena = arenas.get(arenaIndex.get());
        var world = Bukkit.getWorld(arena.world());
        if (world == null) {
            instanceManager.release(dungeonId, arenaIndex.get());
            return Optional.of(StartFailure.WORLD_NOT_FOUND);
        }

        DungeonInstance instance = new DungeonInstance(data, arenaIndex.get());
        for (Player player : party) {
            instance.addMember(player.getUniqueId(), player.getLocation());
        }
        instanceManager.register(instance);
        return Optional.empty();
    }

    /** Teleports every currently-online member of {@code instance} to its arena's entry point - called once the pre-entry countdown elapses. */
    public void teleportIn(DungeonInstance instance) {
        DungeonArena arena = instance.getData().getArenas().get(instance.getArenaIndex());
        var world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return;
        }
        Location entry = new Location(world, arena.x(), arena.y(), arena.z());
        for (UUID memberId : instance.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.teleport(entry);
            }
        }
    }

    /** Ends the run the given player is in (if any), rewarding the whole party only when {@code reason} is {@link DungeonEndReason#CLEARED}. */
    public void finish(UUID playerId, DungeonEndReason reason) {
        DungeonInstance instance = instanceManager.getByPlayer(playerId).orElse(null);
        if (instance == null) {
            return;
        }
        instance.setStatus(switch (reason) {
            case CLEARED -> DungeonInstanceStatus.COMPLETED;
            case TIMED_OUT -> DungeonInstanceStatus.FAILED;
            case RETIRED -> DungeonInstanceStatus.RETIRED;
        });

        for (var entry : instance.getMembers().entrySet()) {
            UUID memberId = entry.getKey();
            Location returnLocation = entry.getValue();
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.teleport(returnLocation);
                if (reason == DungeonEndReason.CLEARED) {
                    statusApi.addExperience(memberId, instance.getData().getRewardExp());
                    if (economy != null) {
                        economy.depositPlayer(member, instance.getData().getRewardMoney());
                    }
                }
            }
        }
        instanceManager.remove(instance.getId());
    }
}
