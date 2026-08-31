package rpg.extra.duel.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import rpg.extra.duel.model.DuelArena;
import rpg.extra.duel.model.DuelSession;
import rpg.extra.duel.repository.DuelArenaRepository;
import rpg.extra.duel.service.DuelArenaAllocator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every currently-active {@link DuelSession}, keyed by both participants' UUIDs (so a
 * lookup from either side is O(1)), and which arena indices are currently occupied.
 */
public final class DuelSessionManager {

    private final DuelArenaRepository arenaRepository;
    private final Map<UUID, DuelSession> sessionsByPlayer = new ConcurrentHashMap<>();
    private final Set<Integer> occupiedArenaIndices = ConcurrentHashMap.newKeySet();

    public DuelSessionManager(DuelArenaRepository arenaRepository) {
        this.arenaRepository = arenaRepository;
    }

    /** Empty if no arena is currently free - caller is responsible for messaging the two players. */
    public Optional<DuelSession> start(Player a, Player b) {
        List<DuelArena> arenas = arenaRepository.getAll();
        Optional<Integer> freeIndex = DuelArenaAllocator.findFreeIndex(arenas.size(), Set.copyOf(occupiedArenaIndices));
        if (freeIndex.isEmpty()) {
            return Optional.empty();
        }
        int index = freeIndex.get();
        DuelArena arena = arenas.get(index);
        DuelSession session = new DuelSession(a.getUniqueId(), b.getUniqueId(),
                a.getLocation().clone(), b.getLocation().clone(), index);
        occupiedArenaIndices.add(index);
        sessionsByPlayer.put(a.getUniqueId(), session);
        sessionsByPlayer.put(b.getUniqueId(), session);
        Location destination = new Location(
                Bukkit.getWorld(arena.world()), arena.x(), arena.y(), arena.z(), arena.yaw(), arena.pitch());
        a.teleport(destination);
        b.teleport(destination);
        return Optional.of(session);
    }

    public Optional<DuelSession> sessionOf(UUID playerId) {
        return Optional.ofNullable(sessionsByPlayer.get(playerId));
    }

    /** Removes {@code session} from tracking and frees its arena - does not teleport/heal anyone, callers (DuelService/DuelDamageListener) do that once, after deciding the outcome. */
    public void end(DuelSession session) {
        sessionsByPlayer.remove(session.getPlayerA());
        sessionsByPlayer.remove(session.getPlayerB());
        occupiedArenaIndices.remove(session.getArenaIndex());
    }
}
