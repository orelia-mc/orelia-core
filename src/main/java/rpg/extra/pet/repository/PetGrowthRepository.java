package rpg.extra.pet.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.extra.pet.model.PetGrowthComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists each player's per-pet-species growth level/experience, one row per
 * {@code (owner, pet_id)} - same shape as {@code rpg.gathering.repository.PlayerGatheringRepository},
 * with a free-form config-defined {@code pet_id} in place of an enum activity type.
 */
public final class PetGrowthRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public PetGrowthRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS pet_growth (
                        uuid VARCHAR(36) NOT NULL,
                        pet_id VARCHAR(64) NOT NULL,
                        level INT NOT NULL DEFAULT 1,
                        experience BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, pet_id)
                    )
                    """);
        }
    }

    public PetGrowthComponent loadOrCreate(UUID uuid) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, Long> experience = new LinkedHashMap<>();
        String sql = "SELECT pet_id, level, experience FROM pet_growth WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String petId = resultSet.getString("pet_id");
                    levels.put(petId, resultSet.getInt("level"));
                    experience.put(petId, resultSet.getLong("experience"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load pet growth data for " + uuid, e);
        }
        return new PetGrowthComponent(uuid, levels, experience);
    }

    public void save(PetGrowthComponent component) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO pet_growth (uuid, pet_id, level, experience) VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid, pet_id) DO UPDATE SET level = excluded.level, experience = excluded.experience
                    """;
            case MYSQL -> """
                    INSERT INTO pet_growth (uuid, pet_id, level, experience) VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE level = VALUES(level), experience = VALUES(experience)
                    """;
        };
        try (Connection connection = databaseManager.getConnection()) {
            for (String petId : component.getLevels().keySet()) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, component.getOwner().toString());
                    statement.setString(2, petId);
                    statement.setInt(3, component.getLevel(petId));
                    statement.setLong(4, component.getExperience(petId));
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save pet growth data for " + component.getOwner(), e);
        }
    }
}
