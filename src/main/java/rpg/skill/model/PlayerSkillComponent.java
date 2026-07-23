package rpg.skill.model;

import rpg.core.player.PlayerDataComponent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player skill progress (persisted: skill points, learned skill levels) and active
 * cooldowns (runtime-only, reset on rejoin/restart).
 */
public final class PlayerSkillComponent implements PlayerDataComponent {

    private final UUID owner;
    private int skillPoints;
    private final Map<String, Integer> skillLevels;
    private final Map<String, Long> cooldownExpiry = new ConcurrentHashMap<>();

    public PlayerSkillComponent(UUID owner, int skillPoints, Map<String, Integer> skillLevels) {
        this.owner = owner;
        this.skillPoints = skillPoints;
        this.skillLevels = new ConcurrentHashMap<>(skillLevels);
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public void addSkillPoints(int amount) {
        this.skillPoints += amount;
    }

    public boolean spendSkillPoint() {
        if (skillPoints <= 0) {
            return false;
        }
        skillPoints--;
        return true;
    }

    /** Subtracts {@code amount}, failing (no change) rather than going negative. */
    public boolean takeSkillPoints(int amount) {
        if (skillPoints < amount) {
            return false;
        }
        skillPoints -= amount;
        return true;
    }

    public void setSkillPoints(int amount) {
        this.skillPoints = Math.max(0, amount);
    }

    public int getSkillLevel(String skillId) {
        return skillLevels.getOrDefault(skillId, 0);
    }

    public void setSkillLevel(String skillId, int level) {
        skillLevels.put(skillId, level);
    }

    public Map<String, Integer> getSkillLevels() {
        return Map.copyOf(skillLevels);
    }

    public boolean isOnCooldown(String skillId, long nowMillis) {
        return cooldownExpiry.getOrDefault(skillId, 0L) > nowMillis;
    }

    public void setCooldown(String skillId, long expiresAtMillis) {
        cooldownExpiry.put(skillId, expiresAtMillis);
    }
}
