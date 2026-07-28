package rpg.region.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionQueryServiceTest {

    @Test
    void unrelatedRegionsAreSortedByPriorityDescending() {
        List<RegionQueryService.RegionEntry> entries = List.of(
                new RegionQueryService.RegionEntry("low", 1, null),
                new RegionQueryService.RegionEntry("high", 10, null),
                new RegionQueryService.RegionEntry("mid", 5, null));

        assertEquals(List.of("high", "mid", "low"), RegionQueryService.orderByEffectivePriority(entries));
    }

    /** The main behavior this whole change is for: a child always outranks its parent. */
    @Test
    void childOutranksParentDespiteLowerPriority() {
        List<RegionQueryService.RegionEntry> entries = List.of(
                new RegionQueryService.RegionEntry("parent_lake", 100, null),
                new RegionQueryService.RegionEntry("child_dock", 1, "parent_lake"));

        assertEquals(List.of("child_dock", "parent_lake"), RegionQueryService.orderByEffectivePriority(entries));
    }

    @Test
    void grandchildOutranksGrandparentAcrossMultipleLevels() {
        List<RegionQueryService.RegionEntry> entries = List.of(
                new RegionQueryService.RegionEntry("grandparent", 100, null),
                new RegionQueryService.RegionEntry("parent", 50, "grandparent"),
                new RegionQueryService.RegionEntry("grandchild", 1, "parent"));

        assertEquals(List.of("grandchild", "parent", "grandparent"),
                RegionQueryService.orderByEffectivePriority(entries));
    }

    @Test
    void siblingsUnderSameParentAreSortedByTheirOwnPriority() {
        List<RegionQueryService.RegionEntry> entries = List.of(
                new RegionQueryService.RegionEntry("parent", 1, null),
                new RegionQueryService.RegionEntry("sibling_a", 5, "parent"),
                new RegionQueryService.RegionEntry("sibling_b", 10, "parent"));

        assertEquals(List.of("sibling_b", "sibling_a", "parent"),
                RegionQueryService.orderByEffectivePriority(entries));
    }

    /** A parent that doesn't itself apply at this location isn't in {@code entries} - the child just falls back to priority. */
    @Test
    void unresolvedParentFallsBackToPriorityComparison() {
        List<RegionQueryService.RegionEntry> entries = List.of(
                new RegionQueryService.RegionEntry("child", 1, "parent_not_here"),
                new RegionQueryService.RegionEntry("other", 5, null));

        assertEquals(List.of("other", "child"), RegionQueryService.orderByEffectivePriority(entries));
    }

    /** Defensive: a malformed self-referencing parent chain must not drop the region from the result. */
    @Test
    void selfReferencingParentDoesNotDropTheRegion() {
        List<RegionQueryService.RegionEntry> entries = List.of(
                new RegionQueryService.RegionEntry("cyclic", 3, "cyclic"));

        assertEquals(List.of("cyclic"), RegionQueryService.orderByEffectivePriority(entries));
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertEquals(List.of(), RegionQueryService.orderByEffectivePriority(List.of()));
    }
}
