package rpg.api;

import java.util.UUID;

/** One row of the level leaderboard (SOW RankingModule), returned by {@link StatusApi#getLeaderboard}. */
public record LeaderboardEntry(UUID uuid, String name, int level, long experience) {
}
