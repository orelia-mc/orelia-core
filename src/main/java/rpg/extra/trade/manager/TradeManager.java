package rpg.extra.trade.manager;

import rpg.extra.trade.model.TradeSession;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active {@link TradeSession}s and pending (unaccepted) trade requests.
 */
public final class TradeManager {

    private final Map<UUID, TradeSession> sessionsByPlayer = new ConcurrentHashMap<>();
    /** target -> requester. */
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();

    public void requestTrade(UUID requesterId, UUID targetId) {
        pendingRequests.put(targetId, requesterId);
    }

    public Optional<UUID> consumeRequest(UUID targetId) {
        return Optional.ofNullable(pendingRequests.remove(targetId));
    }

    public void clearRequest(UUID targetId) {
        pendingRequests.remove(targetId);
    }

    public TradeSession start(UUID playerA, UUID playerB) {
        TradeSession session = new TradeSession(playerA, playerB);
        sessionsByPlayer.put(playerA, session);
        sessionsByPlayer.put(playerB, session);
        return session;
    }

    public Optional<TradeSession> getByPlayer(UUID playerId) {
        return Optional.ofNullable(sessionsByPlayer.get(playerId));
    }

    public void end(TradeSession session) {
        sessionsByPlayer.remove(session.getPlayerA());
        sessionsByPlayer.remove(session.getPlayerB());
    }
}
