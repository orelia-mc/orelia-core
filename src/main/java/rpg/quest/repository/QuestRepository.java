package rpg.quest.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.quest.model.ObjectiveType;
import rpg.quest.model.QuestData;
import rpg.quest.model.QuestObjective;
import rpg.quest.model.QuestReward;
import rpg.quest.model.QuestType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link QuestData}, rebuilt from {@code quests.yml}.
 */
public final class QuestRepository {

    private Map<String, QuestData> quests = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, QuestData> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("quests");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection questSection = section.getConfigurationSection(id);
                if (questSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, questSection));
            }
        }
        this.quests = loaded;
    }

    private QuestData parse(String id, ConfigurationSection section) {
        List<QuestObjective> objectives = new ArrayList<>();
        ConfigurationSection objectivesSection = section.getConfigurationSection("objectives");
        if (objectivesSection != null) {
            for (String objectiveId : objectivesSection.getKeys(false)) {
                ConfigurationSection objectiveSection = objectivesSection.getConfigurationSection(objectiveId);
                if (objectiveSection == null) {
                    continue;
                }
                objectives.add(new QuestObjective(
                        ObjectiveType.valueOf(objectiveSection.getString("type", "KILL_MONSTER").trim().toUpperCase()),
                        objectiveSection.getString("target-id"),
                        objectiveSection.getInt("amount", 1),
                        objectiveSection.getString("world", "world"),
                        objectiveSection.getDouble("x", 0),
                        objectiveSection.getDouble("y", 0),
                        objectiveSection.getDouble("z", 0),
                        objectiveSection.getDouble("radius", 5)));
            }
        }

        QuestReward reward;
        ConfigurationSection rewardSection = section.getConfigurationSection("reward");
        if (rewardSection != null) {
            reward = new QuestReward(
                    rewardSection.getLong("exp", 0),
                    rewardSection.getDouble("money", 0),
                    rewardSection.getString("weapon-id"),
                    rewardSection.getString("accessory-id"),
                    rewardSection.getInt("skill-points", 0),
                    rewardSection.getString("title"),
                    rewardSection.getString("vanilla-material"),
                    rewardSection.getInt("vanilla-amount", 0));
        } else {
            reward = new QuestReward(0, 0, null, null, 0, null, null, 0);
        }

        return new QuestData(
                id,
                section.getString("name", id),
                QuestType.valueOf(section.getString("type", "SUB").trim().toUpperCase()),
                section.getStringList("description"),
                objectives,
                reward,
                section.getBoolean("repeatable", false),
                section.getBoolean("party-only", false),
                section.getInt("required-level", 1),
                section.getStringList("prerequisite-quests"),
                section.getInt("available-hour-start", -1),
                section.getInt("available-hour-end", -1),
                section.getDouble("cooldown-hours", 0));
    }

    public Optional<QuestData> findById(String id) {
        return Optional.ofNullable(quests.get(id));
    }

    public Map<String, QuestData> getAll() {
        return Map.copyOf(quests);
    }
}
