package rpg.extra.duel.service;

import org.junit.jupiter.api.Test;
import rpg.extra.duel.repository.DuelStatsRepository.DuelStatsEntry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuelStatsServiceTest {

    @Test
    void sortsByWinsDescending() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<DuelStatsEntry> input = List.of(
                new DuelStatsEntry(a, 3, 5),
                new DuelStatsEntry(b, 10, 1),
                new DuelStatsEntry(c, 7, 2));

        List<DuelStatsEntry> sorted = DuelStatsService.sortByWinsDescending(input);

        assertEquals(List.of(b, c, a), sorted.stream().map(DuelStatsEntry::uuid).toList());
    }

    @Test
    void stableForEqualWins() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<DuelStatsEntry> input = List.of(
                new DuelStatsEntry(a, 5, 0),
                new DuelStatsEntry(b, 5, 0));

        List<DuelStatsEntry> sorted = DuelStatsService.sortByWinsDescending(input);

        assertEquals(List.of(a, b), sorted.stream().map(DuelStatsEntry::uuid).toList());
    }
}
