package rpg.extra.pet.repository;

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
 * Persists which pets each player has unlocked and which one is currently selected, via
 * orelia-core's shared {@link DatabaseManager}.
 */
public final class PetOwnershipRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public PetOwnershipRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS pet_unlock (
                        owner_uuid VARCHAR(36) NOT NULL,
                        pet_id VARCHAR(64) NOT NULL,
                        unlocked_at BIGINT NOT NULL,
                        PRIMARY KEY (owner_uuid, pet_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS pet_selection (
                        owner_uuid VARCHAR(36) PRIMARY KEY,
                        pet_id VARCHAR(64) NOT NULL
                    )
                    """);
        }
    }

    public Map<UUID, Set<String>> loadUnlocks() {
        Map<UUID, Set<String>> unlocks = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT owner_uuid, pet_id FROM pet_unlock")) {
            while (resultSet.next()) {
                unlocks.computeIfAbsent(UUID.fromString(resultSet.getString("owner_uuid")), k -> new HashSet<>())
                        .add(resultSet.getString("pet_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load pet unlocks", e);
        }
        return unlocks;
    }

    public Map<UUID, String> loadSelections() {
        Map<UUID, String> selections = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT owner_uuid, pet_id FROM pet_selection")) {
            while (resultSet.next()) {
                selections.put(UUID.fromString(resultSet.getString("owner_uuid")), resultSet.getString("pet_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load pet selections", e);
        }
        return selections;
    }

    public void saveUnlock(UUID ownerId, String petId) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO pet_unlock (owner_uuid, pet_id, unlocked_at) VALUES (?, ?, ?) ON CONFLICT(owner_uuid, pet_id) DO NOTHING";
            case MYSQL -> "INSERT IGNORE INTO pet_unlock (owner_uuid, pet_id, unlocked_at) VALUES (?, ?, ?)";
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, petId);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save pet unlock for " + ownerId, e);
        }
    }

    public void saveSelection(UUID ownerId, String petId) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO pet_selection (owner_uuid, pet_id) VALUES (?, ?) ON CONFLICT(owner_uuid) DO UPDATE SET pet_id = excluded.pet_id";
            case MYSQL -> "INSERT INTO pet_selection (owner_uuid, pet_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE pet_id = VALUES(pet_id)";
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, petId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save pet selection for " + ownerId, e);
        }
    }
}
