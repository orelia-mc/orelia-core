package rpg.extra.duel.repository;

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
 * DB-backed win/loss counters, one row per player who has ever finished a duel - mirrors
 * rpg.economy.repository.EconomyRepository/BankRepository's SchemaOwner-on-shared-DatabaseManager
 * convention.
 */
public final class DuelStatsRepository implements SchemaOwner {

    public record DuelStatsEntry(UUID uuid, int wins, int losses) {
    }

    private final DatabaseManager databaseManager;

    public DuelStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS duel_stats (
                        uuid VARCHAR(36) PRIMARY KEY,
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    public void recordWin(UUID uuid) {
        upsert(uuid, 1, 0);
    }

    public void recordLoss(UUID uuid) {
        upsert(uuid, 0, 1);
    }

    private void upsert(UUID uuid, int winsDelta, int lossesDelta) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO duel_stats (uuid, wins, losses) VALUES (?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET wins = wins + excluded.wins, losses = losses + excluded.losses
                    """;
            case MYSQL -> """
                    INSERT INTO duel_stats (uuid, wins, losses) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE wins = wins + VALUES(wins), losses = losses + VALUES(losses)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, winsDelta);
            statement.setInt(3, lossesDelta);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update duel stats for " + uuid, e);
        }
    }

    public List<DuelStatsEntry> topByWins(int limit) {
        String sql = "SELECT uuid, wins, losses FROM duel_stats ORDER BY wins DESC LIMIT ?";
        List<DuelStatsEntry> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new DuelStatsEntry(UUID.fromString(resultSet.getString("uuid")),
                            resultSet.getInt("wins"), resultSet.getInt("losses")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read duel stats leaderboard", e);
        }
        return result;
    }
}
