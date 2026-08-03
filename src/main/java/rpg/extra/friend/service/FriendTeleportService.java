package rpg.extra.friend.service;

import org.bukkit.entity.Player;
import rpg.extra.friend.manager.TeleportRequestManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Request/accept handshake for teleporting to a friend - scoped to existing friends only
 * (validated via {@link FriendService#areFriends}), never a blind/consent-less teleport.
 * Actual teleportation happens in the command layer (needs live {@link Player} objects on
 * both sides), this class only tracks the pending request.
 */
public final class FriendTeleportService {

    public enum ActionResult {
        OK, NOT_FRIENDS, ALREADY_PENDING, NO_PENDING_REQUEST, CANNOT_TARGET_SELF
    }

    private final TeleportRequestManager requestManager;
    private final FriendService friendService;

    public FriendTeleportService(TeleportRequestManager requestManager, FriendService friendService) {
        this.requestManager = requestManager;
        this.friendService = friendService;
    }

    public ActionResult request(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return ActionResult.CANNOT_TARGET_SELF;
        }
        if (!friendService.areFriends(requester.getUniqueId(), target.getUniqueId())) {
            return ActionResult.NOT_FRIENDS;
        }
        if (requestManager.peekRequester(target.getUniqueId()).map(requester.getUniqueId()::equals).orElse(false)) {
            return ActionResult.ALREADY_PENDING;
        }
        requestManager.requestTeleport(requester.getUniqueId(), target.getUniqueId());
        return ActionResult.OK;
    }

    /** Consumes the pending request, returning the requester's id to teleport to {@code target} - empty if none was pending. */
    public Optional<UUID> accept(Player target) {
        return requestManager.consumeTeleportRequest(target.getUniqueId());
    }

    public ActionResult decline(Player target) {
        return requestManager.consumeTeleportRequest(target.getUniqueId()).isPresent()
                ? ActionResult.OK : ActionResult.NO_PENDING_REQUEST;
    }
}
