package rpg.extra.trade.model;

import java.util.UUID;

/**
 * A two-player trade in progress (SOW TradeModule). Not persisted - a server restart
 * cancels any in-flight trade (items are returned by orelia-core's normal player-data
 * save, since they never actually left the players' inventories on disk).
 */
public final class TradeSession {

    private final UUID id = UUID.randomUUID();
    private final UUID playerA;
    private final UUID playerB;
    private final TradeOffer offerA = new TradeOffer();
    private final TradeOffer offerB = new TradeOffer();

    public TradeSession(UUID playerA, UUID playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    public UUID getOtherPlayer(UUID playerId) {
        return playerId.equals(playerA) ? playerB : playerA;
    }

    public TradeOffer offerOf(UUID playerId) {
        return playerId.equals(playerA) ? offerA : offerB;
    }

    public boolean bothConfirmed() {
        return offerA.isConfirmed() && offerB.isConfirmed();
    }
}
