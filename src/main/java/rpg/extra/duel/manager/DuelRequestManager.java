package rpg.extra.duel.manager;

import rpg.core.util.PendingQueue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tracks pending (unanswered) duel requests - mirrors rpg.extra.friend.manager.FriendRequestManager exactly, same PendingQueue-per-target shape. */
public final class DuelRequestManager {

    private final PendingQueue<UUID> pendingRequests = new PendingQueue<>();

    public void request(UUID requesterId, UUID targetId) {
        pendingRequests.add(targetId, requesterId);
    }

    public Optional<UUID> peekOldest(UUID targetId) {
        return pendingRequests.peekOldest(targetId);
    }

    public List<UUID> peekAll(UUID targetId) {
        return pendingRequests.peekAll(targetId);
    }

    public boolean hasPendingFrom(UUID targetId, UUID requesterId) {
        return pendingRequests.peekAll(targetId).contains(requesterId);
    }

    public Optional<UUID> consume(UUID targetId) {
        return pendingRequests.consumeOldest(targetId);
    }

    public Optional<UUID> consume(UUID targetId, UUID requesterId) {
        return pendingRequests.consume(targetId, requesterId);
    }

    public void clear(UUID targetId) {
        pendingRequests.clear(targetId);
    }
}
