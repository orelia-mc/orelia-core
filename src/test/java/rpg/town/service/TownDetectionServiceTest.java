package rpg.town.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownDetectionServiceTest {

    @Test
    void matchesWhenAnyApplicableRegionIsListedAsTown() {
        assertTrue(TownDetectionService.matchesTownRegion(
                List.of("wilderness", "town1_areaa"), Set.of("town1_areaa", "town1_areab")));
    }

    @Test
    void doesNotMatchWhenNoApplicableRegionIsListed() {
        assertFalse(TownDetectionService.matchesTownRegion(
                List.of("wilderness", "dungeon_entrance"), Set.of("town1_areaa", "town1_areab")));
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertTrue(TownDetectionService.matchesTownRegion(
                List.of("Town1_AreaA"), Set.of("town1_areaa")));
    }

    @Test
    void multipleDisjointRegionsCanRepresentTheSameLogicalTown() {
        Set<String> town1 = Set.of("town1_areaa", "town1_areab");
        assertTrue(TownDetectionService.matchesTownRegion(List.of("town1_areaa"), town1));
        assertTrue(TownDetectionService.matchesTownRegion(List.of("town1_areab"), town1));
    }

    @Test
    void emptyTownRegionsNeverMatches() {
        assertFalse(TownDetectionService.matchesTownRegion(List.of("town1_areaa"), Set.of()));
    }

    @Test
    void emptyApplicableRegionsNeverMatches() {
        assertFalse(TownDetectionService.matchesTownRegion(List.of(), Set.of("town1_areaa")));
    }
}
