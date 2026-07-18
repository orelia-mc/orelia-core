package rpg.core.config;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Central registry of the plugin's YAML config files (config.yml, items.yml, skills.yml, ...).
 * Modules ask for their own file by name; Core never inspects module-specific keys.
 */
public final class ConfigManager {

    private final Plugin plugin;
    private final Map<String, ConfigFile> files = new LinkedHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers (or returns the already-registered) config file, copying the bundled
     * default resource of the same name out of the jar the first time it is requested.
     */
    public ConfigFile register(String fileName) {
        return files.computeIfAbsent(fileName, name -> {
            if (plugin.getResource(name) != null) {
                boolean alreadyExisted = new File(plugin.getDataFolder(), name).exists();
                plugin.saveResource(name, false);
                if (alreadyExisted) {
                    migrateExisting(name);
                }
            }
            return new ConfigFile(plugin.getLogger(), plugin.getDataFolder(), name);
        });
    }

    /** Appends any newly-added top-level config-version keys - see {@link ConfigMigrator}. */
    private void migrateExisting(String fileName) {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                return;
            }
            String bundledText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            ConfigMigrator.migrate(plugin.getLogger(), new File(plugin.getDataFolder(), fileName), bundledText);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check " + fileName + " for config migrations", e);
        }
    }

    public ConfigFile get(String fileName) {
        ConfigFile file = files.get(fileName);
        if (file == null) {
            throw new IllegalStateException("Config file not registered: " + fileName);
        }
        return file;
    }

    public void reload(String fileName) {
        get(fileName).reload();
    }

    public void reloadAll() {
        files.values().forEach(ConfigFile::reload);
    }

    /** Names of every config file registered so far (e.g. {@code "config.yml"}), for debug tooling. */
    public Set<String> getRegisteredFileNames() {
        return Set.copyOf(files.keySet());
    }
}
