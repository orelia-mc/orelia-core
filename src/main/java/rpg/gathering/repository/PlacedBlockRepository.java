package rpg.gathering.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.gathering.model.PlacedBlockLocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists coordinates of gather-block-typed blocks a player placed by hand, so they can be
 * excluded from the gathering regen system across restarts (see {@code PlacedBlockTrackingService}).
 */
public final class PlacedBlockRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public PlacedBlockRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS gathering_placed_block (
                        world VARCHAR(64) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        PRIMARY KEY (world, x, y, z)
                    )
                    """);
        }
    }

    public List<PlacedBlockLocation> loadAll() {
        List<PlacedBlockLocation> locations = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM gathering_placed_block")) {
            while (resultSet.next()) {
                locations.add(fromRow(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load placed block locations", e);
        }
        return locations;
    }

    private PlacedBlockLocation fromRow(ResultSet resultSet) throws SQLException {
        return new PlacedBlockLocation(
                resultSet.getString("world"),
                resultSet.getInt("x"),
                resultSet.getInt("y"),
                resultSet.getInt("z"));
    }

    public void insert(PlacedBlockLocation location) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO gathering_placed_block (world, x, y, z) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, location.world());
            statement.setInt(2, location.x());
            statement.setInt(3, location.y());
            statement.setInt(4, location.z());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert placed block location " + location, e);
        }
    }

    public void delete(PlacedBlockLocation location) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gathering_placed_block WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, location.world());
            statement.setInt(2, location.x());
            statement.setInt(3, location.y());
            statement.setInt(4, location.z());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete placed block location " + location, e);
        }
    }
}
