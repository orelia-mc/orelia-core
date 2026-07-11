package rpg.skill.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.item.model.WeaponType;
import rpg.skill.model.SkillData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory registry of every {@link SkillData}, rebuilt from {@code skills.yml}. Adding
 * a skill that reuses an existing {@code executor-type} archetype requires no code change.
 */
public final class SkillRepository {

    private Map<String, SkillData> skills = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, SkillData> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("skills");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection skillSection = section.getConfigurationSection(id);
                if (skillSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, skillSection));
            }
        }
        this.skills = loaded;
    }

    private SkillData parse(String id, ConfigurationSection section) {
        return new SkillData(
                id,
                section.getString("name", id),
                WeaponType.valueOf(section.getString("weapon-type", "SWORD").trim().toUpperCase()),
                section.getString("executor-type", "MELEE_CONE"),
                section.getDouble("sp-cost", 10.0),
                section.getDouble("cooldown-seconds", 5.0),
                section.getDouble("damage-multiplier", 1.5),
                section.getString("effect-particle", "SWEEP_ATTACK"),
                section.getDouble("range", 4.0),
                section.getDouble("radius", 3.0),
                section.getDouble("knockback", 0.4),
                section.getInt("max-level", 5));
    }

    public Optional<SkillData> findById(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    public Map<String, SkillData> getAll() {
        return Map.copyOf(skills);
    }

    public Map<String, SkillData> getByWeaponType(WeaponType type) {
        return skills.values().stream()
                .filter(skill -> skill.getWeaponType() == type)
                .collect(Collectors.toMap(SkillData::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
    }
}
