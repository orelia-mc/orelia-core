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

    /** Adds a brand-new plot to the in-memory registry. Overwrites silently if {@code id} already exists - callers check {@link #findById} first (see {@code HousePlotAdminService#register}). */
    public void add(String id, HousePlot plot) {
        plots.put(id, plot);
    }

    /** Replaces an existing plot's definition in place (e.g. after an admin move). */
    public void replace(String id, HousePlot plot) {
        plots.put(id, plot);
    }

    /** Removes a plot from the in-memory registry. Returns false if {@code id} wasn't present. */
    public boolean remove(String id) {
        return plots.remove(id) != null;
    }
}
