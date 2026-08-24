package rpg.extra.trade.repository;

import org.bukkit.inventory.ItemStack;
import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.extra.trade.model.TradeLogEntry;
import rpg.extra.trade.model.TradeOffer;
import rpg.extra.trade.model.TradeSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Append-only audit log of completed trades (SOW TradeModule follow-up) - an in-progress
 * {@link TradeSession} stays in-memory only, but once a trade executes, a summary is worth
 * keeping for dispute/abuse investigation. Items are stored as a short human-readable
 * summary string rather than a full serialized {@code ItemStack[]} - good enough to answer
 * "what did these two trade", not meant to reconstruct the exact items.
 */
public final class TradeLogRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public TradeLogRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS trade_log (
                        id VARCHAR(36) PRIMARY KEY,
                        player_a_uuid VARCHAR(36) NOT NULL,
                        player_b_uuid VARCHAR(36) NOT NULL,
                        item_summary_a VARCHAR(256),
                        item_summary_b VARCHAR(256),
                        money_a DOUBLE NOT NULL DEFAULT 0,
                        money_b DOUBLE NOT NULL DEFAULT 0,
                        completed_at BIGINT NOT NULL
                    )
                    """);
        }
    }

    public void log(TradeSession session) {
        TradeOffer offerA = session.offerOf(session.getPlayerA());
        TradeOffer offerB = session.offerOf(session.getPlayerB());
        TradeLogEntry entry = new TradeLogEntry(UUID.randomUUID(), session.getPlayerA(), session.getPlayerB(),
                summarize(offerA.getItems()), summarize(offerB.getItems()),
                offerA.getMoney(), offerB.getMoney(), System.currentTimeMillis());
        insert(entry);
    }

    private String summarize(List<ItemStack> items) {
        if (items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> item.getType().name() + " x" + item.getAmount())
                .collect(Collectors.joining(", "));
    }

    private void insert(TradeLogEntry entry) {
        String sql = """
                INSERT INTO trade_log (id, player_a_uuid, player_b_uuid, item_summary_a, item_summary_b, money_a, money_b, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.id().toString());
            statement.setString(2, entry.playerA().toString());
            statement.setString(3, entry.playerB().toString());
            statement.setString(4, entry.itemSummaryA());
            statement.setString(5, entry.itemSummaryB());
            statement.setDouble(6, entry.moneyA());
            statement.setDouble(7, entry.moneyB());
            statement.setLong(8, entry.completedAtMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to log trade " + entry.id(), e);
        }
    }
}
