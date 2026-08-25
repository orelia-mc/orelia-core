package rpg.extra.pet.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import rpg.extra.pet.model.PetDefinition;
import rpg.extra.pet.model.PetDefinition.PetGrowthTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link PetDefinition}, rebuilt from {@code pets.yml}.
 */
public final class PetConfigRepository {

    private Map<String, PetDefinition> pets = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, PetDefinition> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("pets");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection petSection = section.getConfigurationSection(id);
                if (petSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, petSection));
            }
        }
        this.pets = loaded;
    }

    private PetDefinition parse(String id, ConfigurationSection section) {
        return new PetDefinition(
                id,
                section.getString("name", id),
                EntityType.valueOf(section.getString("entity-type", "WOLF").trim().toUpperCase()),
                section.getDouble("price", 0),
                parseGrowth(section.getConfigurationSection("growth")));
    }

    /** Absent {@code growth:} section (most species, opt-in only) returns {@link PetGrowthTemplate#none()}. */
    private PetGrowthTemplate parseGrowth(ConfigurationSection section) {
        if (section == null) {
            return PetGrowthTemplate.none();
        }
        Map<String, Double> perLevelStats = new LinkedHashMap<>();
        ConfigurationSection statsSection = section.getConfigurationSection("per-level");
        if (statsSection != null) {
            for (String statName : statsSection.getKeys(false)) {
                perLevelStats.put(statName, statsSection.getDouble(statName, 0));
            }
        }
        return new PetGrowthTemplate(section.getInt("max-level", 1), section.getLong("exp-per-level", 0), perLevelStats);
    }

    public Optional<PetDefinition> findById(String id) {
        return Optional.ofNullable(pets.get(id));
    }

    public Map<String, PetDefinition> getAll() {
        return Map.copyOf(pets);
    }
}
