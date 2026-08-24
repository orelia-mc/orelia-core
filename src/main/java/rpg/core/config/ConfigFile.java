package rpg.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A single named YAML config file backed by a file on disk, copied from the jar's
 * bundled default on first use. Not thread-safe; config reload/save happens on the
 * main thread via {@code /oladmin reload} or module {@code onReload()} hooks.
 */
public final class ConfigFile {

    private final Logger logger;
    private final File file;
    private final String resourcePath;
    private YamlConfiguration configuration;

    ConfigFile(Logger logger, File dataFolder, String fileName) {
        this.logger = logger;
        this.file = new File(dataFolder, fileName);
        this.resourcePath = fileName;
        load();
    }

    public void load() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        load();
    }

    public void save() {
        try {
            configuration.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save config file: " + resourcePath, e);
        }
    }

    public YamlConfiguration get() {
        return configuration;
    }

    /**
     * Flattens every leaf value (dotted path -> value, section nodes themselves excluded) in
     * the file's current in-memory state - used by {@link ConfigManager#reloadAllWithDiff} to
     * compare before/after a {@code /oladmin reload} and show what an on-disk edit actually
     * changed.
     */
    public Map<String, Object> snapshotLeaves() {
        Map<String, Object> out = new LinkedHashMap<>();
        collectLeaves(configuration, "", out);
        return out;
    }

    private void collectLeaves(ConfigurationSection section, String prefix, Map<String, Object> out) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                collectLeaves(section.getConfigurationSection(key), path, out);
            } else {
                out.put(path, section.get(key));
            }
        }
    }
}
