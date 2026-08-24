package rpg.extra.friend.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists mutual friendships as two rows per relationship ({@code uuid_a -> uuid_b} and
 * {@code uuid_b -> uuid_a}), so listing a player's friends is a single indexed
 * {@code WHERE uuid_a = ?} query instead of an OR/UNION over both columns.
 */
public final class FriendRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public FriendRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS friend (
                        uuid_a VARCHAR(36) NOT NULL,
                        uuid_b VARCHAR(36) NOT NULL,
                        created_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid_a, uuid_b)
                    )
                    """);
        }
    }

    public List<UUID> findFriends(UUID uuid) {
        List<UUID> friends = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid_b FROM friend WHERE uuid_a = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    friends.add(UUID.fromString(resultSet.getString("uuid_b")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load friends for " + uuid, e);
        }
        return friends;
    }

    public boolean areFriends(UUID a, UUID b) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM friend WHERE uuid_a = ? AND uuid_b = ?")) {
            statement.setString(1, a.toString());
            statement.setString(2, b.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check friendship between " + a + " and " + b, e);
        }
    }

    public void addFriendship(UUID a, UUID b) {
        long now = System.currentTimeMillis();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO friend (uuid_a, uuid_b, created_at) VALUES (?, ?, ?)")) {
            statement.setString(1, a.toString());
            statement.setString(2, b.toString());
            statement.setLong(3, now);
            statement.addBatch();
            statement.setString(1, b.toString());
            statement.setString(2, a.toString());
            statement.setLong(3, now);
            statement.addBatch();
            statement.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to add friendship between " + a + " and " + b, e);
        }
    }

    public void removeFriendship(UUID a, UUID b) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM friend WHERE (uuid_a = ? AND uuid_b = ?) OR (uuid_a = ? AND uuid_b = ?)")) {
            statement.setString(1, a.toString());
            statement.setString(2, b.toString());
            statement.setString(3, b.toString());
            statement.setString(4, a.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove friendship between " + a + " and " + b, e);
        }
    }
}
