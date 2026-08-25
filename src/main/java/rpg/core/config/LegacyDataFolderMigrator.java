package rpg.core.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One-shot migration aid for the orelia-core/orelia-world/orelia-extra merge: copies config
 * files an operator had customized under the former plugins' own data folders
 * ({@code plugins/OreliaWorld/}, {@code plugins/OreliaExtra/}) into this plugin's folder, so
 * they aren't silently orphaned when the three jars become one and every config now resolves
 * under {@code plugins/OreliaCore/}.
 *
 * <p>Deliberately conservative: an existing file in the target folder is never overwritten, so
 * this is idempotent and safe to leave running on every startup. {@code config.yml} and
 * {@code messages.yml} are skipped entirely - those two were *content-merged* into
 * orelia-core's own files rather than moved, so copying a former plugin's version over
 * orelia-core's would drop every core setting. Their new sections reach an existing file
 * through {@link ConfigMigrator} instead, which splices in only the missing keys.
 *
 * <p>Temporary: once every server has started at least once on a merged jar, this class and
 * its call in {@code OreliaPlugin#onEnable} can be deleted. (The equivalent temporary aliases
 * for this same merge, {@code /oladmin worldreload}/{@code extrareload}, have already been
 * removed - see git history - now that every server has had a release cycle to adjust.)
 */
public final class LegacyDataFolderMigrator {

    /** Data folder names the former, now-merged plugins used, as siblings of this one. */
    private static final List<String> LEGACY_FOLDERS = List.of("OreliaWorld", "OreliaExtra");

    /** Content-merged rather than moved - see the class javadoc. */
    private static final Set<String> MERGED_NOT_MOVED = Set.of("config.yml", "messages.yml");

    private LegacyDataFolderMigrator() {
    }

    /**
     * Copies every not-yet-present {@code *.yml} from the legacy folders into {@code dataFolder}.
     * Never throws: a failure to copy one file is logged and the rest still run, since a missing
     * customization degrades to the bundled default rather than breaking startup.
     */
    public static void migrate(Logger logger, File dataFolder) {
        File pluginsDir = dataFolder.getParentFile();
        if (pluginsDir == null) {
            return;
        }
        for (String folderName : LEGACY_FOLDERS) {
            File legacyFolder = new File(pluginsDir, folderName);
            if (!legacyFolder.isDirectory()) {
                continue;
            }
            File[] candidates = legacyFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (candidates == null) {
                continue;
            }
            for (File source : candidates) {
                copyIfAbsent(logger, folderName, source, dataFolder);
            }
        }
    }

    private static void copyIfAbsent(Logger logger, String folderName, File source, File dataFolder) {
        String fileName = source.getName();
        if (MERGED_NOT_MOVED.contains(fileName)) {
            return;
        }
        File target = new File(dataFolder, fileName);
        if (target.exists()) {
            return;
        }
        try {
            // The data folder doesn't exist yet on a first run - ConfigManager would create it
            // via saveResource, but that happens after this migration.
            Files.createDirectories(dataFolder.toPath());
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            logger.info("Migrated " + folderName + "/" + fileName + " to this plugin's data folder "
                    + "(the 3-plugin merge moved every config here).");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to migrate " + folderName + "/" + fileName
                    + " - it will fall back to the bundled default. Copy it across by hand if it was customized.", e);
        }
    }
}
