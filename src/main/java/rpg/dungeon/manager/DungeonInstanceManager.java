package rpg.dungeon.manager;

import rpg.dungeon.model.DungeonInstance;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every active {@link DungeonInstance} and which instance (if any) each player is
 * currently inside, plus which arena slot (see {@link rpg.dungeon.model.DungeonArena}) of
 * each dungeon is occupied so at most one run lives in a given arena at a time.
 */
public final class DungeonInstanceManager {

    private final Map<UUID, DungeonInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> monsterToInstance = new ConcurrentHashMap<>();
    private final Map<String, boolean[]> arenaOccupancy = new ConcurrentHashMap<>();

    public void register(DungeonInstance instance) {
        instances.put(instance.getId(), instance);
        instance.getMembers().keySet().forEach(playerId -> playerToInstance.put(playerId, instance.getId()));
    }

    public Optional<DungeonInstance> getByPlayer(UUID playerId) {
        return Optional.ofNullable(playerToInstance.get(playerId)).map(instances::get);
    }

    public void registerMonster(UUID entityId, UUID instanceId) {
        monsterToInstance.put(entityId, instanceId);
    }

    public Optional<DungeonInstance> getByMonster(UUID entityId) {
        return Optional.ofNullable(monsterToInstance.get(entityId)).map(instances::get);
    }

    public void remove(UUID instanceId) {
        DungeonInstance instance = instances.remove(instanceId);
        if (instance != null) {
            instance.getMembers().keySet().forEach(playerToInstance::remove);
            instance.getAliveMonsterIds().forEach(monsterToInstance::remove);
            release(instance.getData().getId(), instance.getArenaIndex());
        }
    }

    public void removePlayer(UUID playerId) {
        playerToInstance.remove(playerId);
    }

    /**
     * Reserves the first free arena slot (0-indexed) for {@code dungeonId}, sized to
     * {@code arenaCount} arenas. Returns empty if every slot is currently occupied by an
     * active run - the caller should reject the challenge as "dungeon full" in that case.
     */
    public synchronized Optional<Integer> tryAcquireArena(String dungeonId, int arenaCount) {
        boolean[] occupancy = arenaOccupancy.computeIfAbsent(dungeonId, id -> new boolean[arenaCount]);
        if (occupancy.length != arenaCount) {
            occupancy = Arrays.copyOf(occupancy, arenaCount);
            arenaOccupancy.put(dungeonId, occupancy);
        }
        for (int i = 0; i < occupancy.length; i++) {
            if (!occupancy[i]) {
                occupancy[i] = true;
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /** Frees an arena slot reserved by {@link #tryAcquireArena} without registering an instance (e.g. world lookup failed after reserving). */
    public synchronized void release(String dungeonId, int arenaIndex) {
        boolean[] occupancy = arenaOccupancy.get(dungeonId);
        if (occupancy != null && arenaIndex >= 0 && arenaIndex < occupancy.length) {
            occupancy[arenaIndex] = false;
        }
    }
}
