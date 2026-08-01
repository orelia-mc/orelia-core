package rpg.status.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code config.yml: status.leveling} - the experience curve used to turn kill/quest
 * EXP rewards into level-ups.
 */
public final class LevelingConfig {

    private long expPerLevel = 100;
    private int maxLevel = 80;

    public void load(YamlConfiguration config) {
        this.expPerLevel = config.getLong("status.leveling.exp-per-level", 100);
        this.maxLevel = config.getInt("status.leveling.max-level", 80);
    }

    /** Total experience required to advance from {@code level} to {@code level + 1}. */
    public long requiredExperience(int level) {
        return expPerLevel * level;
    }

    public int getMaxLevel() {
        return maxLevel;
    }
}
