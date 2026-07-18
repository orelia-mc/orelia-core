package rpg.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    private static final Logger LOGGER = Logger.getLogger(ConfigMigratorTest.class.getName());

    @Test
    void appendsMissingTopLevelSectionFromBundledDefault(@TempDir Path tempDir) throws IOException {
        String existingText = """
                config-version: 1

                # Existing section.
                database:
                  type: SQLITE
                """;
        String bundledText = """
                config-version: 2

                # Existing section.
                database:
                  type: SQLITE

                # A brand new section added in version 2.
                new-feature:
                  enabled: true
                """;

        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, existingText, StandardCharsets.UTF_8);

        ConfigMigrator.migrate(LOGGER, file.toFile(), bundledText);

        String result = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(result.contains("# A brand new section added in version 2."));
        assertTrue(result.contains("new-feature:"));
        assertTrue(result.contains("enabled: true"));
        // Existing content is untouched, not reformatted/reordered.
        assertTrue(result.startsWith(existingText));
    }

    @Test
    void doesNothingWhenExistingVersionIsAlreadyCurrent(@TempDir Path tempDir) throws IOException {
        String existingText = """
                config-version: 2

                database:
                  type: SQLITE
                """;
        String bundledText = """
                config-version: 2

                database:
                  type: SQLITE

                new-feature:
                  enabled: true
                """;

        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, existingText, StandardCharsets.UTF_8);

        ConfigMigrator.migrate(LOGGER, file.toFile(), bundledText);

        String result = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(result.contains("new-feature"));
    }
}
