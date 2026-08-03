package rpg.extra.housing.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.extra.housing.model.HousePlot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link HousePlot}, rebuilt from {@code housing.yml}.
 */
public final class HousePlotRepository {

    private Map<String, HousePlot> plots = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, HousePlot> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("plots");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection plotSection = section.getConfigurationSection(id);
                if (plotSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, plotSection));
            }
        }
        this.plots = loaded;
    }

    private HousePlot parse(String id, ConfigurationSection section) {
        return new HousePlot(
                id,
                section.getString("name", id),
                section.getDouble("price", 0),
                section.getString("world", "world"),
                section.getDouble("x", 0),
                section.getDouble("y", 64),
                section.getDouble("z", 0),
                (float) section.getDouble("yaw", 0));
    }

    public Optional<HousePlot> findById(String id) {
        return Optional.ofNullable(plots.get(id));
    }

    public Map<String, HousePlot> getAll() {
        return Map.copyOf(plots);
    }
}
