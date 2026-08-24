package rpg.core.config;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
                // saveResource(name, false) logs its own "could not save, already exists"
                // warning whenever the file is already there and replace=false - calling it
                // unconditionally spammed that warning on every single startup after the
                // first. Only ever need it the first time the file doesn't exist yet.
                if (!new File(plugin.getDataFolder(), name).exists()) {
                    plugin.saveResource(name, false);
                } else {
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
        reloadAllWithDiff();
    }

    /** One leaf key's before/after value in a reloaded file - either side is {@code null} for a key that was added/removed on disk. */
    public record KeyChange(String path, String before, String after) {
    }

    /** Every changed leaf key in one config file, in {@code /oladmin reload}'s report. Only present for a file that actually changed. */
    public record FileDiff(String fileName, List<KeyChange> changes) {
    }

    /**
     * Reloads every registered file (same effect as {@link #reloadAll}) and reports which leaf
     * keys actually changed value, were added, or were removed - lets an admin who hand-edited
     * a yml file on disk and ran {@code /oladmin reload} see exactly what took effect, instead
     * of a plain "reloaded" with no way to confirm the edit was even read correctly.
     */
    public List<FileDiff> reloadAllWithDiff() {
        Map<String, Map<String, Object>> before = new LinkedHashMap<>();
        files.forEach((name, file) -> before.put(name, file.snapshotLeaves()));
        files.values().forEach(ConfigFile::reload);

        List<FileDiff> diffs = new ArrayList<>();
        for (Map.Entry<String, ConfigFile> entry : files.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> beforeSnapshot = before.get(name);
            Map<String, Object> afterSnapshot = entry.getValue().snapshotLeaves();
            List<KeyChange> changes = diffSnapshots(beforeSnapshot, afterSnapshot);
            if (!changes.isEmpty()) {
                diffs.add(new FileDiff(name, changes));
            }
        }
        return diffs;
    }

    private List<KeyChange> diffSnapshots(Map<String, Object> before, Map<String, Object> after) {
        Set<String> allPaths = new TreeSet<>(new LinkedHashSet<>(before.keySet()));
        allPaths.addAll(after.keySet());
        List<KeyChange> changes = new ArrayList<>();
        for (String path : allPaths) {
            boolean hadBefore = before.containsKey(path);
            boolean hasAfter = after.containsKey(path);
            Object beforeValue = before.get(path);
            Object afterValue = after.get(path);
            boolean valueChanged = hadBefore && hasAfter && !java.util.Objects.equals(beforeValue, afterValue);
            if (!hadBefore || !hasAfter || valueChanged) {
                changes.add(new KeyChange(path,
                        hadBefore ? String.valueOf(beforeValue) : null,
                        hasAfter ? String.valueOf(afterValue) : null));
            }
        }
        return changes;
    }

    /** Names of every config file registered so far (e.g. {@code "config.yml"}), for debug tooling. */
    public Set<String> getRegisteredFileNames() {
        return Set.copyOf(files.keySet());
    }
}
