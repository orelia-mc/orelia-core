package rpg.extra.mount.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.extra.mount.model.MountGrowthComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists each player's per-mount-species growth level/experience, one row per
 * {@code (owner, mount_id)} - same shape as {@code rpg.extra.pet.repository.PetGrowthRepository}.
 */
public final class MountGrowthRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public MountGrowthRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mount_growth (
                        uuid VARCHAR(36) NOT NULL,
                        mount_id VARCHAR(64) NOT NULL,
                        level INT NOT NULL DEFAULT 1,
                        experience BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, mount_id)
                    )
                    """);
        }
    }

    public MountGrowthComponent loadOrCreate(UUID uuid) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, Long> experience = new LinkedHashMap<>();
        String sql = "SELECT mount_id, level, experience FROM mount_growth WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String mountId = resultSet.getString("mount_id");
                    levels.put(mountId, resultSet.getInt("level"));
                    experience.put(mountId, resultSet.getLong("experience"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mount growth data for " + uuid, e);
        }
        return new MountGrowthComponent(uuid, levels, experience);
    }

    public void save(MountGrowthComponent component) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO mount_growth (uuid, mount_id, level, experience) VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid, mount_id) DO UPDATE SET level = excluded.level, experience = excluded.experience
                    """;
            case MYSQL -> """
                    INSERT INTO mount_growth (uuid, mount_id, level, experience) VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE level = VALUES(level), experience = VALUES(experience)
                    """;
        };
        try (Connection connection = databaseManager.getConnection()) {
            for (String mountId : component.getLevels().keySet()) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, component.getOwner().toString());
                    statement.setString(2, mountId);
                    statement.setInt(3, component.getLevel(mountId));
                    statement.setLong(4, component.getExperience(mountId));
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save mount growth data for " + component.getOwner(), e);
        }
    }
}
