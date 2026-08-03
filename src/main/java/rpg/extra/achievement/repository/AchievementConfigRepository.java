package rpg.extra.achievement.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.extra.achievement.model.AchievementDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory registry of every {@link AchievementDefinition}, rebuilt from
 * {@code achievements.yml}.
 */
public final class AchievementConfigRepository {

    private Map<String, AchievementDefinition> achievements = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, AchievementDefinition> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("achievements");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection achievementSection = section.getConfigurationSection(id);
                if (achievementSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, achievementSection));
            }
        }
        this.achievements = loaded;
    }

    private AchievementDefinition parse(String id, ConfigurationSection section) {
        return new AchievementDefinition(
                id,
                section.getString("name", id),
                section.getString("description", ""),
                section.getString("category", "その他"),
                AchievementDefinition.ConditionType.valueOf(section.getString("condition-type", "REACH_LEVEL").trim().toUpperCase()),
                section.getString("condition-value", "0"),
                section.getInt("reward-skill-points", 0));
    }

    public Map<String, AchievementDefinition> getAll() {
        return Map.copyOf(achievements);
    }
}
