package rpg.extra.housing.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists which plot (if any) each player owns, via orelia-core's shared
 * {@link DatabaseManager}. One house per player.
 */
public final class HouseOwnershipRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public HouseOwnershipRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS house_ownership (
                        owner_uuid VARCHAR(36) PRIMARY KEY,
                        plot_id VARCHAR(64) NOT NULL,
                        purchased_at BIGINT NOT NULL
                    )
                    """);
        }
    }

    public Map<UUID, String> loadAll() {
        Map<UUID, String> ownership = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT owner_uuid, plot_id FROM house_ownership")) {
            while (resultSet.next()) {
                ownership.put(UUID.fromString(resultSet.getString("owner_uuid")), resultSet.getString("plot_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load house ownership", e);
        }
        return ownership;
    }

    public void save(UUID ownerId, String plotId) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO house_ownership (owner_uuid, plot_id, purchased_at) VALUES (?, ?, ?)
                    ON CONFLICT(owner_uuid) DO UPDATE SET plot_id = excluded.plot_id, purchased_at = excluded.purchased_at
                    """;
            case MYSQL -> """
                    INSERT INTO house_ownership (owner_uuid, plot_id, purchased_at) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE plot_id = VALUES(plot_id), purchased_at = VALUES(purchased_at)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, plotId);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save house ownership for " + ownerId, e);
        }
    }

    public void delete(UUID ownerId) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM house_ownership WHERE owner_uuid = ?")) {
            statement.setString(1, ownerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete house ownership for " + ownerId, e);
        }
    }

}
