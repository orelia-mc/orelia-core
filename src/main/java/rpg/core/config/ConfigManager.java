package rpg.core.config;

import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
                plugin.saveResource(name, false);
            }
            return new ConfigFile(plugin.getLogger(), plugin.getDataFolder(), name);
        });
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
