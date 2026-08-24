package rpg.extra.mount.repository;

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
 * Persists which mounts each player has unlocked and which one is currently selected, via
 * orelia-core's shared {@link DatabaseManager}.
 */
public final class MountOwnershipRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public MountOwnershipRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mount_unlock (
                        owner_uuid VARCHAR(36) NOT NULL,
                        mount_id VARCHAR(64) NOT NULL,
                        unlocked_at BIGINT NOT NULL,
                        PRIMARY KEY (owner_uuid, mount_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mount_selection (
                        owner_uuid VARCHAR(36) PRIMARY KEY,
                        mount_id VARCHAR(64) NOT NULL
                    )
                    """);
        }
    }

    public Map<UUID, Set<String>> loadUnlocks() {
        Map<UUID, Set<String>> unlocks = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT owner_uuid, mount_id FROM mount_unlock")) {
            while (resultSet.next()) {
                unlocks.computeIfAbsent(UUID.fromString(resultSet.getString("owner_uuid")), k -> new HashSet<>())
                        .add(resultSet.getString("mount_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mount unlocks", e);
        }
        return unlocks;
    }

    public Map<UUID, String> loadSelections() {
        Map<UUID, String> selections = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT owner_uuid, mount_id FROM mount_selection")) {
            while (resultSet.next()) {
                selections.put(UUID.fromString(resultSet.getString("owner_uuid")), resultSet.getString("mount_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mount selections", e);
        }
        return selections;
    }

    public void saveUnlock(UUID ownerId, String mountId) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO mount_unlock (owner_uuid, mount_id, unlocked_at) VALUES (?, ?, ?) ON CONFLICT(owner_uuid, mount_id) DO NOTHING";
            case MYSQL -> "INSERT IGNORE INTO mount_unlock (owner_uuid, mount_id, unlocked_at) VALUES (?, ?, ?)";
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, mountId);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save mount unlock for " + ownerId, e);
        }
    }

    public void saveSelection(UUID ownerId, String mountId) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO mount_selection (owner_uuid, mount_id) VALUES (?, ?) ON CONFLICT(owner_uuid) DO UPDATE SET mount_id = excluded.mount_id";
            case MYSQL -> "INSERT INTO mount_selection (owner_uuid, mount_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE mount_id = VALUES(mount_id)";
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, mountId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save mount selection for " + ownerId, e);
        }
    }
}
