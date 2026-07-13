package rpg.gathering.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.gathering.model.BlockRegenTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists pending block-regeneration tasks (SOW 3.1 "server restart data protection") so
 * a block broken right before a crash still restores once the server comes back.
 */
public final class BlockRegenRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public BlockRegenRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS block_regen_task (
                        id VARCHAR(36) PRIMARY KEY,
                        world VARCHAR(64) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        original_material VARCHAR(64) NOT NULL,
                        restore_at_millis BIGINT NOT NULL
                    )
                    """);
        }
    }

    public List<BlockRegenTask> loadAll() {
        List<BlockRegenTask> tasks = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM block_regen_task")) {
            while (resultSet.next()) {
                tasks.add(fromRow(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load block regen tasks", e);
        }
        return tasks;
    }

    private BlockRegenTask fromRow(ResultSet resultSet) throws SQLException {
        return new BlockRegenTask(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("world"),
                resultSet.getInt("x"),
                resultSet.getInt("y"),
                resultSet.getInt("z"),
                resultSet.getString("original_material"),
                resultSet.getLong("restore_at_millis"));
    }

    public void save(BlockRegenTask task) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO block_regen_task (id, world, x, y, z, original_material, restore_at_millis) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, task.id().toString());
            statement.setString(2, task.world());
            statement.setInt(3, task.x());
            statement.setInt(4, task.y());
            statement.setInt(5, task.z());
            statement.setString(6, task.originalMaterial());
            statement.setLong(7, task.restoreAtMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save block regen task " + task.id(), e);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM block_regen_task WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete block regen task " + id, e);
        }
    }
}
