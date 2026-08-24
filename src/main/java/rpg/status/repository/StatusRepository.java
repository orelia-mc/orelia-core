package rpg.status.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.status.model.LeaderboardEntry;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.LevelGrowthService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists the parts of {@link PlayerStatusComponent} that must survive a restart:
 * level, experience, current HP/SP. Equipment contributions and buffs are runtime-only
 * and are rebuilt by the item/accessory/skill modules on join.
 */
public final class StatusRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;
    private final LevelGrowthService levelGrowthService;

    public StatusRepository(DatabaseManager databaseManager, LevelGrowthService levelGrowthService) {
        this.databaseManager = databaseManager;
        this.levelGrowthService = levelGrowthService;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_status (
                        uuid VARCHAR(36) PRIMARY KEY,
                        level INTEGER NOT NULL DEFAULT 1,
                        experience BIGINT NOT NULL DEFAULT 0,
                        current_hp DOUBLE NOT NULL DEFAULT 0,
                        current_sp DOUBLE NOT NULL DEFAULT 0
                    )
                    """);
            migrateCurrentMpColumn(connection, statement);
            migrateNameColumn(connection, statement);
        }
    }

    /** One-time migration for installs created before the MP stat was renamed to SP. */
    private void migrateCurrentMpColumn(Connection connection, Statement statement) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "player_status", "current_mp")) {
            if (columns.next()) {
                statement.execute("ALTER TABLE player_status RENAME COLUMN current_mp TO current_sp");
            }
        }
    }

    /**
     * One-time migration for installs created before the leaderboard needed a name column.
     * Existing rows get a NULL name until their owner's next join re-syncs it via
     * {@link #syncName} (see {@link rpg.core.player.PlayerNameSyncListener}).
     */
    private void migrateNameColumn(Connection connection, Statement statement) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "player_status", "name")) {
            if (!columns.next()) {
                statement.execute("ALTER TABLE player_status ADD COLUMN name VARCHAR(32)");
            }
        }
    }

    public PlayerStatusComponent loadOrCreate(UUID uuid) {
        String sql = "SELECT level, experience, current_hp, current_sp FROM player_status WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int level = resultSet.getInt("level");
                    StatSheet baseStats = levelGrowthService.baseStatsForLevel(level);
                    return new PlayerStatusComponent(
                            uuid,
                            level,
                            resultSet.getLong("experience"),
                            baseStats,
                            resultSet.getDouble("current_hp"),
                            resultSet.getDouble("current_sp"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load status for " + uuid, e);
        }

        StatSheet baseStats = levelGrowthService.baseStatsForLevel(1);
        return new PlayerStatusComponent(uuid, 1, 0L, baseStats, baseStats.get(StatType.HP), baseStats.get(StatType.SP));
    }

    private static final String UNKNOWN_NAME_PLACEHOLDER = "???";

    /** Top {@code limit} players by level (ties broken by experience). Name comes from
     * {@code player_status.name}, synced on join via {@link #syncName} (SOW RankingModule) -
     * a row that hasn't seen its owner join since this column was added falls back to
     * {@value #UNKNOWN_NAME_PLACEHOLDER}. */
    public List<LeaderboardEntry> findTopByLevel(int limit) {
        String sql = """
                SELECT uuid, name, level, experience FROM player_status
                ORDER BY level DESC, experience DESC
                LIMIT ?
                """;
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = resultSet.getString("name");
                    entries.add(new LeaderboardEntry(
                            UUID.fromString(resultSet.getString("uuid")),
                            name != null ? name : UNKNOWN_NAME_PLACEHOLDER,
                            resultSet.getInt("level"),
                            resultSet.getLong("experience")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load level leaderboard", e);
        }
        return entries;
    }

    /**
     * Upserts just the {@code name} column, called from {@link rpg.core.player.PlayerNameSyncListener}
     * on join. Other columns fall back to {@code player_status}'s own defaults (level=1,
     * experience=0, ...) when inserting a row that doesn't exist yet - the subsequent
     * {@link #loadOrCreate}/{@link #save} calls for that player own the real values.
     */
    public void syncName(UUID uuid, String name) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO player_status (uuid, name) VALUES (?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET name = excluded.name
                    """;
            case MYSQL -> """
                    INSERT INTO player_status (uuid, name) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE name = VALUES(name)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to sync name for " + uuid, e);
        }
    }

    public void save(PlayerStatusComponent component) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO player_status (uuid, level, experience, current_hp, current_sp) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET level = excluded.level, experience = excluded.experience,
                        current_hp = excluded.current_hp, current_sp = excluded.current_sp
                    """;
            case MYSQL -> """
                    INSERT INTO player_status (uuid, level, experience, current_hp, current_sp) VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE level = VALUES(level), experience = VALUES(experience),
                        current_hp = VALUES(current_hp), current_sp = VALUES(current_sp)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, component.getOwner().toString());
            statement.setInt(2, component.getLevel());
            statement.setLong(3, component.getExperience());
            statement.setDouble(4, component.getCurrentHp());
            statement.setDouble(5, component.getCurrentSp());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save status for " + component.getOwner(), e);
        }
    }
}
