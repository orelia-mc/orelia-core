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

    /** Final (post equipment/buff) stat values keyed by stat name (HP, SP, ATK, DEF, CRT, CRT_DMG, SPD). */
    Map<String, Double> getFinalStats(UUID playerId);

    /** Current (live, not max) HP - see {@code getFinalStats}'s {@code "HP"} entry for the max value. */
    Optional<Double> getCurrentHp(UUID playerId);

    /** Current (live, not max) SP - see {@code getFinalStats}'s {@code "SP"} entry for the max value. */
    Optional<Double> getCurrentSp(UUID playerId);

    void addExperience(UUID playerId, long amount);

    boolean tryConsumeSp(UUID playerId, double amount);

    void damage(UUID playerId, double amount);

    void heal(UUID playerId, double amount);

    /** Top players by level (ties broken by experience); includes offline players. */
    List<LeaderboardEntry> getLeaderboard(int limit);

    /**
     * Sets a named, replace-wholesale stat contribution (e.g. a summoned pet/mount's growth
     * bonus) - same mechanism accessories/job use internally, keyed by an arbitrary
     * {@code sourceKey} so multiple contributions stack without clobbering each other. Unknown
     * stat names (a typo in a caller's own config) are skipped, not fatal. No-op if
     * {@code playerId} has no loaded status component (e.g. offline).
     */
    void setEquipmentContribution(UUID playerId, String sourceKey, Map<String, Double> stats);

    /** Clears a previously-set {@link #setEquipmentContribution} for {@code sourceKey} (e.g. on pet/mount dismiss). No-op if none was set. */
    void clearEquipmentContribution(UUID playerId, String sourceKey);
}
