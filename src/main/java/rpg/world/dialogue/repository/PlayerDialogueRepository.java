package rpg.world.dialogue.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.world.dialogue.model.PlayerDialogueComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persists dialogue flags via orelia-core's shared {@link DatabaseManager} (looked up
 * from Bukkit's ServicesManager, not orelia-core's internal DatabaseModule).
 */
public final class PlayerDialogueRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public PlayerDialogueRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS dialogue_flag (
                        uuid VARCHAR(36) NOT NULL,
                        flag VARCHAR(64) NOT NULL,
                        PRIMARY KEY (uuid, flag)
                    )
                    """);
        }
    }

    public PlayerDialogueComponent loadOrCreate(UUID uuid) {
        Set<String> flags = new HashSet<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT flag FROM dialogue_flag WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    flags.add(resultSet.getString("flag"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load dialogue flags for " + uuid, e);
        }
        return new PlayerDialogueComponent(uuid, flags);
    }

    public void save(PlayerDialogueComponent component) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> "INSERT INTO dialogue_flag (uuid, flag) VALUES (?, ?) ON CONFLICT(uuid, flag) DO NOTHING";
            case MYSQL -> "INSERT IGNORE INTO dialogue_flag (uuid, flag) VALUES (?, ?)";
        };
        try (Connection connection = databaseManager.getConnection()) {
            for (String flag : component.getFlags()) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, component.getOwner().toString());
                    statement.setString(2, flag);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save dialogue flags for " + component.getOwner(), e);
        }
    }
}
