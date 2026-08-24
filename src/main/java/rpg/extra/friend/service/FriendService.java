package rpg.extra.friend.service;

import org.bukkit.entity.Player;
import rpg.extra.friend.manager.FriendRequestManager;
import rpg.extra.friend.repository.FriendRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Friend list business rules on top of {@link FriendRepository}'s plain data operations:
 * request/accept/decline handshake (mirrors {@code TradeService}'s pending-request pattern)
 * and mutual add/remove.
 */
public final class FriendService {

    public enum ActionResult {
        OK, ALREADY_FRIENDS, ALREADY_PENDING, CANNOT_TARGET_SELF, NO_PENDING_REQUEST, NOT_FRIENDS, FRIEND_LIST_FULL
    }

    private final FriendRepository repository;
    private final FriendRequestManager requestManager;
    private final int maxFriends;

    public FriendService(FriendRepository repository, FriendRequestManager requestManager, int maxFriends) {
        this.repository = repository;
        this.requestManager = requestManager;
        this.maxFriends = maxFriends;
    }

    public ActionResult request(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return ActionResult.CANNOT_TARGET_SELF;
        }
        if (repository.areFriends(requester.getUniqueId(), target.getUniqueId())) {
            return ActionResult.ALREADY_FRIENDS;
        }
        if (requestManager.peekRequester(target.getUniqueId()).map(requester.getUniqueId()::equals).orElse(false)) {
            return ActionResult.ALREADY_PENDING;
        }
        if (repository.findFriends(requester.getUniqueId()).size() >= maxFriends) {
            return ActionResult.FRIEND_LIST_FULL;
        }
        requestManager.requestFriend(requester.getUniqueId(), target.getUniqueId());
        return ActionResult.OK;
    }

    public ActionResult accept(Player target) {
        UUID requesterId = requestManager.consumeFriendRequest(target.getUniqueId()).orElse(null);
        if (requesterId == null) {
            return ActionResult.NO_PENDING_REQUEST;
        }
        if (repository.findFriends(target.getUniqueId()).size() >= maxFriends) {
            return ActionResult.FRIEND_LIST_FULL;
        }
        repository.addFriendship(requesterId, target.getUniqueId());
        return ActionResult.OK;
    }

    public ActionResult decline(Player target) {
        return requestManager.consumeFriendRequest(target.getUniqueId()).isPresent()
                ? ActionResult.OK : ActionResult.NO_PENDING_REQUEST;
    }

    /**
     * Looks at {@code targetId}'s pending incoming request without consuming it - callers use
     * this to learn who to notify once {@link #accept}/{@link #decline} (which do consume it)
     * report success, since those two don't hand the requester id back themselves.
     */
    public Optional<UUID> peekPendingRequester(UUID targetId) {
        return requestManager.peekRequester(targetId);
    }

    public ActionResult remove(Player player, UUID friendId) {
        if (!repository.areFriends(player.getUniqueId(), friendId)) {
            return ActionResult.NOT_FRIENDS;
        }
        repository.removeFriendship(player.getUniqueId(), friendId);
        return ActionResult.OK;
    }

    public List<UUID> listFriends(UUID playerId) {
        return repository.findFriends(playerId);
    }

    public boolean areFriends(UUID a, UUID b) {
        return repository.areFriends(a, b);
    }
}
