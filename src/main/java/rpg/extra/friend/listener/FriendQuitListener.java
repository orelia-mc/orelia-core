package rpg.extra.friend.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.core.message.MessageManager;
import rpg.extra.friend.manager.FriendRequestManager;
import rpg.extra.friend.manager.TeleportRequestManager;

/** Clears any pending friend/teleport request the moment either side disconnects, notifying the other side if one was pending. */
public final class FriendQuitListener implements Listener {

    private final FriendRequestManager friendRequestManager;
    private final TeleportRequestManager teleportRequestManager;
    private final MessageManager messages;

    public FriendQuitListener(FriendRequestManager friendRequestManager, TeleportRequestManager teleportRequestManager,
                               MessageManager messages) {
        this.friendRequestManager = friendRequestManager;
        this.teleportRequestManager = teleportRequestManager;
        this.messages = messages;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        friendRequestManager.consumeFriendRequest(player.getUniqueId())
                .map(Bukkit::getPlayer)
                .ifPresent(requester -> messages.send(requester, "friend.request-cancelled-by-quit", "player", player.getName()));
        friendRequestManager.clearFriendRequest(player.getUniqueId());

        teleportRequestManager.consumeTeleportRequest(player.getUniqueId())
                .map(Bukkit::getPlayer)
                .ifPresent(requester -> messages.send(requester, "friend.tpa-cancelled-by-quit", "player", player.getName()));
        teleportRequestManager.clearTeleportRequest(player.getUniqueId());
    }
}
