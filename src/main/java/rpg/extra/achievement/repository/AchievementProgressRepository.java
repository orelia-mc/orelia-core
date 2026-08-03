package rpg.extra.achievement.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persists which achievements each player has unlocked, via orelia-core's shared
 * {@link DatabaseManager}.
 */
public final class AchievementProgressRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public AchievementProgressRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS achievement_unlock (
                        owner_uuid VARCHAR(36) NOT NULL,
                        achievement_id VARCHAR(64) NOT NULL,
                        unlocked_at BIGINT NOT NULL,
                        PRIMARY KEY (owner_uuid, achievement_id)
                    )
                    """);
        }
    }

    public Map<UUID, Set<String>> loadAll() {
        Map<UUID, Set<String>> unlocked = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT owner_uuid, achievement_id FROM achievement_unlock")) {
            while (resultSet.next()) {
                unlocked.computeIfAbsent(UUID.fromString(resultSet.getString("owner_uuid")), k -> new HashSet<>())
                        .add(resultSet.getString("achievement_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load achievement progress", e);
        }
        return unlocked;
    }

    public void saveUnlock(UUID ownerId, String achievementId) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO achievement_unlock (owner_uuid, achievement_id, unlocked_at) VALUES (?, ?, ?) ON CONFLICT(owner_uuid, achievement_id) DO NOTHING";
            case MYSQL -> "INSERT IGNORE INTO achievement_unlock (owner_uuid, achievement_id, unlocked_at) VALUES (?, ?, ?)";
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, achievementId);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save achievement unlock for " + ownerId, e);
        }
    }
}
