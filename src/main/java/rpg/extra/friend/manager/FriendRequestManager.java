package rpg.extra.friend.manager;

import rpg.core.util.PendingQueue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tracks pending (unaccepted) friend requests - multiple requesters can be queued per target at once, oldest first. */
public final class FriendRequestManager {

    /** target -> ordered requester ids. */
    private final PendingQueue<UUID> pendingRequests = new PendingQueue<>();

    public void requestFriend(UUID requesterId, UUID targetId) {
        pendingRequests.add(targetId, requesterId);
    }

    /** Looks at the target's oldest pending request without consuming it - used for the no-argument "/friend accept". */
    public Optional<UUID> peekRequester(UUID targetId) {
        return pendingRequests.peekOldest(targetId);
    }

    /** Every requester currently queued for {@code targetId}, oldest first - for the GUI's ordered list. */
    public List<UUID> peekAllRequesters(UUID targetId) {
        return pendingRequests.peekAll(targetId);
    }

    /** Whether {@code requesterId} specifically already has a request queued to {@code targetId} - used to reject a duplicate re-request rather than queuing it twice. */
    public boolean hasPendingFrom(UUID targetId, UUID requesterId) {
        return pendingRequests.peekAll(targetId).contains(requesterId);
    }

    public Optional<UUID> consumeFriendRequest(UUID targetId) {
        return pendingRequests.consumeOldest(targetId);
    }

    /** Consumes one specific requester's pending request (not necessarily the oldest) - for "/friend accept &lt;name&gt;" and the GUI's per-entry buttons. */
    public Optional<UUID> consumeFriendRequest(UUID targetId, UUID requesterId) {
        return pendingRequests.consume(targetId, requesterId);
    }

    public void clearFriendRequest(UUID targetId) {
        pendingRequests.clear(targetId);
    }
}
