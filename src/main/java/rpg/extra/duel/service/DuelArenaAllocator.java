package rpg.extra.duel.service;

import java.util.Optional;
import java.util.Set;

/**
 * Pure index-selection logic for picking a free duel arena - kept Bukkit-independent (works on
 * plain ints/indices, not Location/DuelArena objects) so it's directly unit-testable, same
 * reasoning rpg.region.service.RegionQueryService#orderByEffectivePriority is pulled out pure.
 */
public final class DuelArenaAllocator {

    private DuelArenaAllocator() {
    }

    /** First 0-based index in [0, totalArenas) not present in occupiedIndices, empty if every arena is occupied (or none exist). */
    public static Optional<Integer> findFreeIndex(int totalArenas, Set<Integer> occupiedIndices) {
        for (int i = 0; i < totalArenas; i++) {
            if (!occupiedIndices.contains(i)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
