package rpg.gathering.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegenExclusionServiceTest {

    @Test
    void matchesWhenAnyApplicableRegionIsExcluded() {
        assertTrue(RegenExclusionService.matchesExcludedRegion(
                List.of("wilderness", "town1_plaza"), Set.of("town1_plaza", "player_base_alice")));
    }

    @Test
    void doesNotMatchWhenNoApplicableRegionIsExcluded() {
        assertFalse(RegenExclusionService.matchesExcludedRegion(
                List.of("wilderness", "mining_field"), Set.of("town1_plaza", "player_base_alice")));
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertTrue(RegenExclusionService.matchesExcludedRegion(
                List.of("Town1_Plaza"), Set.of("town1_plaza")));
    }

    @Test
    void disjointRegionsCanCoverOneLogicalBuildArea() {
        Set<String> excluded = Set.of("town1_plaza", "town1_market");
        assertTrue(RegenExclusionService.matchesExcludedRegion(List.of("town1_plaza"), excluded));
        assertTrue(RegenExclusionService.matchesExcludedRegion(List.of("town1_market"), excluded));
    }

    @Test
    void emptyExcludedRegionsNeverMatches() {
        assertFalse(RegenExclusionService.matchesExcludedRegion(List.of("town1_plaza"), Set.of()));
    }

    /** Fail-open: WorldGuard absent or nothing applicable means gathering stays enabled. */
    @Test
    void emptyApplicableRegionsNeverMatches() {
        assertFalse(RegenExclusionService.matchesExcludedRegion(List.of(), Set.of("town1_plaza")));
    }
}
