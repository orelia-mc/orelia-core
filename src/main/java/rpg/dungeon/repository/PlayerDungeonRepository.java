package rpg.dungeon.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.dungeon.model.PlayerDungeonComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists a player's dungeon unlock log. Mirrors {@code rpg.quest.repository.PlayerQuestRepository}'s
 * completed-quest table/upsert shape.
 */
public final class PlayerDungeonRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public PlayerDungeonRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS dungeon_unlocked (
                        uuid VARCHAR(36) NOT NULL,
                        dungeon_id VARCHAR(64) NOT NULL,
                        unlocked_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, dungeon_id)
                    )
                    """);
        }
    }

    public PlayerDungeonComponent loadOrCreate(UUID uuid) {
        Map<String, Instant> unlocked = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT dungeon_id, unlocked_at FROM dungeon_unlocked WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    unlocked.put(resultSet.getString("dungeon_id"), Instant.ofEpochMilli(resultSet.getLong("unlocked_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load unlocked dungeons for " + uuid, e);
        }
        return new PlayerDungeonComponent(uuid, unlocked);
    }

    /**
     * Direct DB lookup, independent of {@link rpg.core.player.PlayerDataManager}'s in-memory
     * component - used to check a dungeon party's leader even when the leader isn't online.
     */
    public boolean isUnlocked(UUID uuid, String dungeonId) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM dungeon_unlocked WHERE uuid = ? AND dungeon_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, dungeonId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check unlocked dungeon " + dungeonId + " for " + uuid, e);
        }
    }

    public void save(PlayerDungeonComponent component) {
        try (Connection connection = databaseManager.getConnection()) {
            for (Map.Entry<String, Instant> entry : component.getUnlockedDungeonsWithTimestamps().entrySet()) {
                upsertUnlocked(connection, component.getOwner(), entry.getKey(), entry.getValue().toEpochMilli());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save unlocked dungeons for " + component.getOwner(), e);
        }
    }

    private void upsertUnlocked(Connection connection, UUID uuid, String dungeonId, long unlockedAtEpochMillis) throws SQLException {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO dungeon_unlocked (uuid, dungeon_id, unlocked_at) VALUES (?, ?, ?) ON CONFLICT(uuid, dungeon_id) DO UPDATE SET unlocked_at = excluded.unlocked_at";
            case MYSQL -> "INSERT INTO dungeon_unlocked (uuid, dungeon_id, unlocked_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE unlocked_at = VALUES(unlocked_at)";
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, dungeonId);
            statement.setLong(3, unlockedAtEpochMillis);
            statement.executeUpdate();
        }
    }
}
