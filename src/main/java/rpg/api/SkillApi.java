package rpg.api;

import java.util.List;
import java.util.UUID;

/**
 * Cross-plugin surface over the skill module - primarily so orelia-world's QuestModule
 * can grant skill acquisition points as a quest reward, and orelia-debug can inspect/adjust
 * a player's balance for testing.
 */
public interface SkillApi {

    /** Adds to the player's skill acquisition point balance (spent to learn/upgrade skills). */
    void grantSkillPoints(UUID playerId, int amount);

    int getSkillPoints(UUID playerId);

    /** Subtracts skill acquisition points, failing (no change) rather than going negative. */
    boolean takeSkillPoints(UUID playerId, int amount);

    void setSkillPoints(UUID playerId, int amount);

    int getSkillLevel(UUID playerId, String skillId);

    /** Every skill the player has learned (level &gt; 0), across all weapon types. */
    List<SkillSummary> getLearnedSkills(UUID playerId);
}
