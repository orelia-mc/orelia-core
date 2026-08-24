package rpg.extra.trade.model;

import java.util.UUID;

/**
 * One completed trade's audit record (SOW TradeModule follow-up) - a trade itself stays
 * in-memory only ({@link TradeSession} is never persisted), but a completed trade's summary
 * is worth keeping for dispute/abuse investigation after the fact.
 */
public record TradeLogEntry(UUID id, UUID playerA, UUID playerB, String itemSummaryA, String itemSummaryB,
                             double moneyA, double moneyB, long completedAtMillis) {
}
