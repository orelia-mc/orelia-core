package rpg.monster.spawnpoint.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.monster.spawnpoint.model.MonsterSpawnPoint;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists {@link MonsterSpawnPoint}s so admin-placed spawn points survive a restart.
 */
public final class MonsterSpawnPointRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public MonsterSpawnPointRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS monster_spawn_point (
                        id VARCHAR(36) PRIMARY KEY,
                        monster_id VARCHAR(64) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        interval_seconds INT NOT NULL,
                        max_alive INT NOT NULL,
                        target_level INTEGER
                    )
                    """);
            migrateTargetLevelColumn(connection, statement);
        }
    }

    /** One-time migration for installs created before per-spawn-point level scaling existed. */
    private void migrateTargetLevelColumn(Connection connection, Statement statement) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "monster_spawn_point", "target_level")) {
            if (!columns.next()) {
                statement.execute("ALTER TABLE monster_spawn_point ADD COLUMN target_level INTEGER");
            }
        }
    }

    public List<MonsterSpawnPoint> loadAll() {
        List<MonsterSpawnPoint> points = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM monster_spawn_point")) {
            while (resultSet.next()) {
                points.add(fromRow(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load monster spawn points", e);
        }
        return points;
    }

    private MonsterSpawnPoint fromRow(ResultSet resultSet) throws SQLException {
        int rawTargetLevel = resultSet.getInt("target_level");
        Integer targetLevel = resultSet.wasNull() ? null : rawTargetLevel;
        return new MonsterSpawnPoint(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("monster_id"),
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getInt("interval_seconds"),
                resultSet.getInt("max_alive"),
                targetLevel);
    }

    public void save(MonsterSpawnPoint point) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO monster_spawn_point (id, monster_id, world, x, y, z, interval_seconds, max_alive, target_level) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, point.getId().toString());
            statement.setString(2, point.getMonsterId());
            statement.setString(3, point.getWorld());
            statement.setDouble(4, point.getX());
            statement.setDouble(5, point.getY());
            statement.setDouble(6, point.getZ());
            statement.setInt(7, point.getIntervalSeconds());
            statement.setInt(8, point.getMaxAlive());
            if (point.getTargetLevel() != null) {
                statement.setInt(9, point.getTargetLevel());
            } else {
                statement.setNull(9, Types.INTEGER);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save monster spawn point " + point.getId(), e);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM monster_spawn_point WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete monster spawn point " + id, e);
        }
    }
}
