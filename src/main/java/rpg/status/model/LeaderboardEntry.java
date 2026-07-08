package rpg.status.model;

import java.util.UUID;

/** One row of the level leaderboard (SOW RankingModule, via {@code StatusApi}). */
public record LeaderboardEntry(UUID uuid, String name, int level, long experience) {
}
