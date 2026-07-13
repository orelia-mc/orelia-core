package rpg.gathering.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.gathering.model.PlayerGatheringComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Persists the player's gathering/farming level and experience.
 */
public final class PlayerGatheringRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public PlayerGatheringRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_gathering (
                        uuid VARCHAR(36) PRIMARY KEY,
                        level INT NOT NULL,
                        experience BIGINT NOT NULL
                    )
                    """);
        }
    }

    public PlayerGatheringComponent loadOrCreate(UUID uuid) {
        String sql = "SELECT level, experience FROM player_gathering WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new PlayerGatheringComponent(uuid, resultSet.getInt("level"), resultSet.getLong("experience"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load gathering data for " + uuid, e);
        }
        return new PlayerGatheringComponent(uuid, 1, 0L);
    }

    public void save(PlayerGatheringComponent component) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO player_gathering (uuid, level, experience) VALUES (?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET level = excluded.level, experience = excluded.experience
                    """;
            case MYSQL -> """
                    INSERT INTO player_gathering (uuid, level, experience) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE level = VALUES(level), experience = VALUES(experience)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, component.getOwner().toString());
            statement.setInt(2, component.getLevel());
            statement.setLong(3, component.getExperience());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save gathering data for " + component.getOwner(), e);
        }
    }
}
