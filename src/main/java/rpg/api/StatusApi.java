package rpg.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-plugin surface over the status module. Published via Bukkit's
 * {@code ServicesManager} so orelia-world (quest rewards, dungeon rewards, ...) never
 * depends on {@code rpg.status} internals directly.
 */
public interface StatusApi {

    Optional<Integer> getLevel(UUID playerId);

    /** Final (post equipment/buff) stat values keyed by stat name (HP, MP, STR, DEF, AGI, DEX, INT, LUK). */
    Map<String, Double> getFinalStats(UUID playerId);

    void addExperience(UUID playerId, long amount);

    boolean tryConsumeMp(UUID playerId, double amount);

    void damage(UUID playerId, double amount);

    void heal(UUID playerId, double amount);

    /** Top players by level (ties broken by experience); includes offline players. */
    List<LeaderboardEntry> getLeaderboard(int limit);
}
