package rpg.extra.mount.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import rpg.extra.mount.model.MountDefinition;
import rpg.extra.mount.model.MountDefinition.MountGrowthTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link MountDefinition}, rebuilt from {@code mounts.yml}.
 */
public final class MountConfigRepository {

    private Map<String, MountDefinition> mounts = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, MountDefinition> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("mounts");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection mountSection = section.getConfigurationSection(id);
                if (mountSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, mountSection));
            }
        }
        this.mounts = loaded;
    }

    private MountDefinition parse(String id, ConfigurationSection section) {
        return new MountDefinition(
                id,
                section.getString("name", id),
                EntityType.valueOf(section.getString("entity-type", "HORSE").trim().toUpperCase()),
                section.getDouble("speed", 0.2),
                section.getDouble("price", 0),
                parseGrowth(section.getConfigurationSection("growth")));
    }

    /** Absent {@code growth:} section (most species, opt-in only) returns {@link MountGrowthTemplate#none()}. */
    private MountGrowthTemplate parseGrowth(ConfigurationSection section) {
        if (section == null) {
            return MountGrowthTemplate.none();
        }
        Map<String, Double> perLevelStats = new LinkedHashMap<>();
        ConfigurationSection statsSection = section.getConfigurationSection("per-level");
        if (statsSection != null) {
            for (String statName : statsSection.getKeys(false)) {
                perLevelStats.put(statName, statsSection.getDouble(statName, 0));
            }
        }
        return new MountGrowthTemplate(section.getInt("max-level", 1), section.getLong("exp-per-level", 0), perLevelStats);
    }

    public Optional<MountDefinition> findById(String id) {
        return Optional.ofNullable(mounts.get(id));
    }

    public Map<String, MountDefinition> getAll() {
        return Map.copyOf(mounts);
    }
}
