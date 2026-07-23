package rpg.gathering.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.model.PlayerGatheringComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists the player's gathering level/experience, tracked independently per
 * {@link GatherActionType} (mining/woodcutting/farming).
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
                    CREATE TABLE IF NOT EXISTS player_gathering_activity (
                        uuid VARCHAR(36) NOT NULL,
                        activity VARCHAR(32) NOT NULL,
                        level INT NOT NULL DEFAULT 1,
                        experience BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, activity)
                    )
                    """);
        }
    }

    public PlayerGatheringComponent loadOrCreate(UUID uuid) {
        Map<GatherActionType, Integer> levels = new EnumMap<>(GatherActionType.class);
        Map<GatherActionType, Long> experience = new EnumMap<>(GatherActionType.class);
        String sql = "SELECT activity, level, experience FROM player_gathering_activity WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    GatherActionType type;
                    try {
                        type = GatherActionType.valueOf(resultSet.getString("activity"));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    levels.put(type, resultSet.getInt("level"));
                    experience.put(type, resultSet.getLong("experience"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load gathering data for " + uuid, e);
        }
        return new PlayerGatheringComponent(uuid, levels, experience);
    }

    public void save(PlayerGatheringComponent component) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO player_gathering_activity (uuid, activity, level, experience) VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid, activity) DO UPDATE SET level = excluded.level, experience = excluded.experience
                    """;
            case MYSQL -> """
                    INSERT INTO player_gathering_activity (uuid, activity, level, experience) VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE level = VALUES(level), experience = VALUES(experience)
                    """;
        };
        try (Connection connection = databaseManager.getConnection()) {
            for (GatherActionType type : GatherActionType.values()) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, component.getOwner().toString());
                    statement.setString(2, type.name());
                    statement.setInt(3, component.getLevel(type));
                    statement.setLong(4, component.getExperience(type));
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save gathering data for " + component.getOwner(), e);
        }
    }
}
