package rpg.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds newly-introduced top-level config keys to an existing user config file without
 * touching anything else in it. Deliberately never loads-then-resaves the user's file through
 * {@link YamlConfiguration} - that would strip every comment, which this codebase's config
 * files rely on heavily. Instead, missing keys are appended as raw text (comments included,
 * copied verbatim from the bundled default), and keys the user has that the bundled default
 * no longer defines are only logged as a warning, never removed automatically.
 *
 * <p>Gated by a {@code config-version} integer at the top of the file: migration only runs
 * when the bundled resource's version is higher than the user's file's (missing = version 0),
 * so an up-to-date file is never re-scanned on every startup.
 *
 * <p>Block extraction assumes this codebase's own config.yml convention: every top-level
 * section is separated from the next by a blank line, with any explanatory {@code #} comments
 * directly above the key they document. A section that doesn't follow this shape simply won't
 * be auto-appended - the admin adds it by hand, same as before this feature existed.
 */
final class ConfigMigrator {

    private ConfigMigrator() {
    }

    static void migrate(Logger logger, File file, String bundledText) {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new StringReader(bundledText));
        YamlConfiguration existing = YamlConfiguration.loadConfiguration(file);

        int bundledVersion = bundled.getInt("config-version", 0);
        int existingVersion = existing.getInt("config-version", 0);
        if (existingVersion >= bundledVersion) {
            return;
        }

        var bundledKeys = bundled.getKeys(false);
        var existingKeys = existing.getKeys(false);
        Map<String, String> blocks = extractTopLevelBlocks(bundledText);

        StringBuilder appended = new StringBuilder();
        for (String key : bundledKeys) {
            if (!existingKeys.contains(key) && blocks.containsKey(key)) {
                appended.append("\n").append(blocks.get(key));
            }
        }

        for (String key : existingKeys) {
            if (!bundledKeys.contains(key)) {
                logger.warning("Config key '" + key + "' in " + file.getName() + " is no longer used by this version - you can remove it.");
            }
        }

        if (appended.length() == 0) {
            return;
        }
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, true)) {
            writer.write(appended.toString());
            logger.info("Added new default config keys to " + file.getName() + " - see the file for details.");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to append new config keys to " + file.getName(), e);
        }
    }

    private static Map<String, String> extractTopLevelBlocks(String text) {
        Map<String, String> blocks = new LinkedHashMap<>();
        for (String chunk : text.split("\n\\s*\n")) {
            String keyLine = null;
            for (String line : chunk.split("\n")) {
                if (line.matches("^[A-Za-z0-9_.-]+:.*")) {
                    keyLine = line;
                    break;
                }
            }
            if (keyLine != null) {
                String key = keyLine.substring(0, keyLine.indexOf(':'));
                blocks.putIfAbsent(key, chunk.strip() + "\n");
            }
        }
        return blocks;
    }
}
