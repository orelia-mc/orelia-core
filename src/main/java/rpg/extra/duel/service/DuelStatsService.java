package rpg.extra.duel.service;

import rpg.extra.duel.repository.DuelStatsRepository;
import rpg.extra.duel.repository.DuelStatsRepository.DuelStatsEntry;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Business logic over {@link DuelStatsRepository} - records a finished duel's result and serves the leaderboard. */
public final class DuelStatsService {

    private final DuelStatsRepository repository;

    public DuelStatsService(DuelStatsRepository repository) {
        this.repository = repository;
    }

    public void recordResult(UUID winner, UUID loser) {
        repository.recordWin(winner);
        repository.recordLoss(loser);
    }

    public List<DuelStatsEntry> topByWins(int limit) {
        return sortByWinsDescending(repository.topByWins(limit));
    }

    /** Pure, DB-independent - {@code repository.topByWins} already orders by SQL, but this is the single place that decision lives so it's directly testable without a database. */
    static List<DuelStatsEntry> sortByWinsDescending(List<DuelStatsEntry> entries) {
        return entries.stream()
                .sorted(Comparator.comparingInt(DuelStatsEntry::wins).reversed())
                .toList();
    }
}
