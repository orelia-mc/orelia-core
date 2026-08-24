package rpg.dungeon.repository;

import org.bukkit.Location;
import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.dungeon.model.DungeonBlockKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Location -> dungeon id registry for the trigger blocks admins place with
 * {@code /oladmin dungeonblock set/remove}. {@link org.bukkit.event.player.PlayerInteractEvent}
 * fires on the main thread on every right-click, so lookups are served from an in-memory
 * cache loaded once at startup ({@link #loadAll()}) - only {@link #place}/{@link #remove}
 * touch the database.
 */
public final class DungeonBlockRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;
    private final Map<DungeonBlockKey, String> byLocation = new ConcurrentHashMap<>();

    public DungeonBlockRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS dungeon_block (
                        world VARCHAR(64) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        dungeon_id VARCHAR(64) NOT NULL,
                        PRIMARY KEY (world, x, y, z)
                    )
                    """);
        }
    }

    /** Populates the in-memory cache from the database. Call once, right after {@link #createSchemaIfNotExists()}. */
    public void loadAll() {
        byLocation.clear();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT world, x, y, z, dungeon_id FROM dungeon_block")) {
            while (resultSet.next()) {
                DungeonBlockKey key = new DungeonBlockKey(resultSet.getString("world"),
                        resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"));
                byLocation.put(key, resultSet.getString("dungeon_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load dungeon trigger blocks", e);
        }
    }

    public Optional<String> findDungeonId(Location location) {
        return Optional.ofNullable(byLocation.get(DungeonBlockKey.of(location)));
    }

    public void place(Location location, String dungeonId) {
        DungeonBlockKey key = DungeonBlockKey.of(location);
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO dungeon_block (world, x, y, z, dungeon_id) VALUES (?, ?, ?, ?, ?) ON CONFLICT(world, x, y, z) DO UPDATE SET dungeon_id = excluded.dungeon_id";
            case MYSQL -> "INSERT INTO dungeon_block (world, x, y, z, dungeon_id) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE dungeon_id = VALUES(dungeon_id)";
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.world());
            statement.setInt(2, key.x());
            statement.setInt(3, key.y());
            statement.setInt(4, key.z());
            statement.setString(5, dungeonId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to place dungeon trigger block", e);
        }
        byLocation.put(key, dungeonId);
    }

    /** Removes the trigger registration at this location, if any. Returns whether one existed. */
    public boolean remove(Location location) {
        DungeonBlockKey key = DungeonBlockKey.of(location);
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM dungeon_block WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, key.world());
            statement.setInt(2, key.x());
            statement.setInt(3, key.y());
            statement.setInt(4, key.z());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove dungeon trigger block", e);
        }
        return byLocation.remove(key) != null;
    }

    public Map<DungeonBlockKey, String> getAll() {
        return Map.copyOf(byLocation);
    }
}
