package rpg.extra.friend.manager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending (unaccepted) teleport-to-friend requests - a separate pool from
 * {@link FriendRequestManager}'s friend requests, so both can be pending between the same
 * two players at once. One pending request per target at a time.
 */
public final class TeleportRequestManager {

    /** target -> requester. */
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();

    public void requestTeleport(UUID requesterId, UUID targetId) {
        pendingRequests.put(targetId, requesterId);
    }

    /** Looks at the pending request without consuming it - used to detect a duplicate request from the same requester. */
    public Optional<UUID> peekRequester(UUID targetId) {
        return Optional.ofNullable(pendingRequests.get(targetId));
    }

    public Optional<UUID> consumeTeleportRequest(UUID targetId) {
        return Optional.ofNullable(pendingRequests.remove(targetId));
    }

    public void clearTeleportRequest(UUID targetId) {
        pendingRequests.remove(targetId);
    }
}
