package rpg.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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
}
