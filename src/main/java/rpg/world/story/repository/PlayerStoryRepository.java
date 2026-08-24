package rpg.world.story.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.world.story.model.PlayerStoryComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persists story progress (current chapter, completed chapters, flags, unlocked endings)
 * via orelia-core's shared {@link DatabaseManager}.
 */
public final class PlayerStoryRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public PlayerStoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS story_progress (
                        uuid VARCHAR(36) PRIMARY KEY,
                        current_chapter VARCHAR(64)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS story_completed_chapter (
                        uuid VARCHAR(36) NOT NULL,
                        chapter_id VARCHAR(64) NOT NULL,
                        PRIMARY KEY (uuid, chapter_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS story_flag (
                        uuid VARCHAR(36) NOT NULL,
                        flag VARCHAR(64) NOT NULL,
                        PRIMARY KEY (uuid, flag)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS story_ending (
                        uuid VARCHAR(36) NOT NULL,
                        ending_id VARCHAR(64) NOT NULL,
                        PRIMARY KEY (uuid, ending_id)
                    )
                    """);
        }
    }

    public PlayerStoryComponent loadOrCreate(UUID uuid) {
        String currentChapter = null;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT current_chapter FROM story_progress WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    currentChapter = resultSet.getString("current_chapter");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load story progress for " + uuid, e);
        }

        Set<String> completedChapters = readSet(uuid, "story_completed_chapter", "chapter_id");
        Set<String> flags = readSet(uuid, "story_flag", "flag");
        Set<String> endings = readSet(uuid, "story_ending", "ending_id");
        return new PlayerStoryComponent(uuid, currentChapter, completedChapters, flags, endings);
    }

    private Set<String> readSet(UUID uuid, String table, String column) {
        Set<String> values = new HashSet<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + column + " FROM " + table + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getString(column));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load " + table + " for " + uuid, e);
        }
        return values;
    }

    public void save(PlayerStoryComponent component) {
        String progressSql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO story_progress (uuid, current_chapter) VALUES (?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET current_chapter = excluded.current_chapter
                    """;
            case MYSQL -> """
                    INSERT INTO story_progress (uuid, current_chapter) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE current_chapter = VALUES(current_chapter)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(progressSql)) {
            statement.setString(1, component.getOwner().toString());
            statement.setString(2, component.getCurrentChapterId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save story progress for " + component.getOwner(), e);
        }

        writeSet(component.getOwner(), "story_completed_chapter", "chapter_id", component.getCompletedChapterIds());
        writeSet(component.getOwner(), "story_flag", "flag", component.getFlags());
        writeSet(component.getOwner(), "story_ending", "ending_id", component.getUnlockedEndingIds());
    }

    private void writeSet(UUID uuid, String table, String column, Set<String> values) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO " + table + " (uuid, " + column + ") VALUES (?, ?) ON CONFLICT(uuid, " + column + ") DO NOTHING";
            case MYSQL -> "INSERT IGNORE INTO " + table + " (uuid, " + column + ") VALUES (?, ?)";
        };
        try (Connection connection = databaseManager.getConnection()) {
            for (String value : values) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, value);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save " + table + " for " + uuid, e);
        }
    }
}
