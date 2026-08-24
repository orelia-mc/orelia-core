package rpg.extra.friend.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.friend.gui.FriendGuiScreen;
import rpg.extra.friend.service.FriendService;
import rpg.extra.friend.service.FriendTeleportService;
import rpg.gui.framework.GuiManager;
import rpg.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /ol friend add|accept|decline|remove|list|gui|tpa|tpaccept|tpdecline} (SOW follow-up
 * "フレンド機能"). Teleport requests are scoped to existing friends only - never a
 * consent-less teleport.
 */
public final class FriendCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "add", "accept", "decline", "remove", "list", "gui", "tpa", "tpaccept", "tpdecline");
    private static final List<String> ONLINE_PLAYER_TARGET_ACTIONS = List.of("add", "tpa");

    private final FriendService friendService;
    private final FriendTeleportService teleportService;
    private final MessageManager messages;
    private final FriendGuiScreen guiScreen;
    private final GuiManager guiManager;

    public FriendCommand(FriendService friendService, FriendTeleportService teleportService, MessageManager messages,
                          FriendGuiScreen guiScreen, GuiManager guiManager) {
        this.friendService = friendService;
        this.teleportService = teleportService;
        this.messages = messages;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage.friend");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.friend-add");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "command.player-not-found", "player", args[1]);
                    return true;
                }
                FriendService.ActionResult result = friendService.request(player, target);
                report(sender, result, "friend.request-sent");
                if (result == FriendService.ActionResult.OK) {
                    sendFriendRequestNotification(target, player);
                }
            }
            case "accept" -> report(sender, friendService.accept(player), "friend.accepted");
            case "decline" -> report(sender, friendService.decline(player), "friend.declined");
            case "remove" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.friend-remove");
                    return true;
                }
                UUID friendId = resolveFriendByName(player, args[1]).orElse(null);
                if (friendId == null) {
                    messages.send(sender, "friend.not-friends");
                    return true;
                }
                report(sender, friendService.remove(player, friendId), "friend.removed");
            }
            case "list" -> listFriends(sender, player);
            case "gui" -> guiManager.open(player, guiScreen.build(player));
            case "tpa" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.friend-tpa");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "command.player-not-found", "player", args[1]);
                    return true;
                }
                FriendTeleportService.ActionResult result = teleportService.request(player, target);
                reportTpa(sender, result, "friend.tpa-sent");
                if (result == FriendTeleportService.ActionResult.OK) {
                    sendTeleportRequestNotification(target, player);
                }
            }
            case "tpaccept" -> {
                UUID requesterId = teleportService.accept(player).orElse(null);
                if (requesterId == null) {
                    messages.send(sender, "friend.tpa-no-pending-request");
                    return true;
                }
                Player requester = Bukkit.getPlayer(requesterId);
                if (requester == null) {
                    messages.send(sender, "friend.tpa-no-pending-request");
                    return true;
                }
                requester.teleport(player.getLocation());
                messages.send(player, "friend.tpa-accepted-target", "player", requester.getName());
                messages.send(requester, "friend.tpa-accepted-requester", "player", player.getName());
            }
            case "tpdecline" -> reportTpa(sender, teleportService.decline(player), "friend.tpa-declined");
            default -> messages.send(sender, "usage.friend");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length != 2) {
            return List.of();
        }
        if (ONLINE_PLAYER_TARGET_ACTIONS.stream().anyMatch(args[0]::equalsIgnoreCase)) {
            return TabCompletions.onlinePlayerNames(args[1]);
        }
        if (args[0].equalsIgnoreCase("remove") && sender instanceof Player player) {
            return TabCompletions.matching(friendNames(player), args[1]);
        }
        return List.of();
    }

    /** Resolves {@code name} to a UUID only among {@code viewer}'s current friends - avoids the deprecated by-name offline-player lookup, and rejects a typo'd name that isn't actually a friend. */
    private Optional<UUID> resolveFriendByName(Player viewer, String name) {
        for (UUID friendId : friendService.listFriends(viewer.getUniqueId())) {
            String friendName = Bukkit.getOfflinePlayer(friendId).getName();
            if (name.equalsIgnoreCase(friendName)) {
                return Optional.of(friendId);
            }
        }
        return Optional.empty();
    }

    private List<String> friendNames(Player viewer) {
        List<String> names = new ArrayList<>();
        for (UUID friendId : friendService.listFriends(viewer.getUniqueId())) {
            String name = Bukkit.getOfflinePlayer(friendId).getName();
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    /** Sends the request text plus clickable 承認/拒否 buttons (same UX as party invites). */
    private void sendFriendRequestNotification(Player invitee, Player requester) {
        messages.send(invitee, "friend.request-received", "player", requester.getName());
        Component accept = ColorUtil.component(messages.format("friend.request-accept-button"))
                .clickEvent(ClickEvent.runCommand("/ol friend accept"))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(messages.format("friend.request-accept-hover"))));
        Component decline = ColorUtil.component(messages.format("friend.request-decline-button"))
                .clickEvent(ClickEvent.runCommand("/ol friend decline"))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(messages.format("friend.request-decline-hover"))));
        invitee.sendMessage(Component.text(" ").append(accept).append(Component.text("   ")).append(decline));
    }

    private void sendTeleportRequestNotification(Player invitee, Player requester) {
        messages.send(invitee, "friend.tpa-received", "player", requester.getName());
        Component accept = ColorUtil.component(messages.format("friend.tpa-accept-button"))
                .clickEvent(ClickEvent.runCommand("/ol friend tpaccept"))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(messages.format("friend.tpa-accept-hover"))));
        Component decline = ColorUtil.component(messages.format("friend.tpa-decline-button"))
                .clickEvent(ClickEvent.runCommand("/ol friend tpdecline"))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(messages.format("friend.tpa-decline-hover"))));
        invitee.sendMessage(Component.text(" ").append(accept).append(Component.text("   ")).append(decline));
    }

    private void listFriends(CommandSender sender, Player player) {
        List<UUID> friends = friendService.listFriends(player.getUniqueId());
        if (friends.isEmpty()) {
            messages.send(sender, "friend.no-friends");
            return;
        }
        messages.send(sender, "friend.list-header");
        for (UUID friendId : friends) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(friendId);
            Player online = Bukkit.getPlayer(friendId);
            String name = offline.getName();
            if (online != null) {
                Component chatButton = ColorUtil.component(messages.format("friend.chat-button"))
                        .clickEvent(ClickEvent.suggestCommand("/ol msg " + name + " "))
                        .hoverEvent(HoverEvent.showText(ColorUtil.component(messages.format("friend.chat-button-hover"))));
                Component tpButton = ColorUtil.component(messages.format("friend.tp-button"))
                        .clickEvent(ClickEvent.runCommand("/ol friend tpa " + name))
                        .hoverEvent(HoverEvent.showText(ColorUtil.component(messages.format("friend.tp-button-hover"))));
                sender.sendMessage(ColorUtil.component(messages.format("friend.list-entry-online", "name", name))
                        .append(Component.text(" "))
                        .append(chatButton)
                        .append(Component.text(" "))
                        .append(tpButton));
            } else {
                messages.sendRaw(sender, "friend.list-entry-offline", "name", name);
            }
        }
    }

    private void report(CommandSender sender, FriendService.ActionResult result, String successKey) {
        if (result == FriendService.ActionResult.OK) {
            messages.send(sender, successKey);
            return;
        }
        String key = switch (result) {
            case ALREADY_FRIENDS -> "friend.already-friends";
            case ALREADY_PENDING -> "friend.already-pending";
            case CANNOT_TARGET_SELF -> "friend.cannot-target-self";
            case NO_PENDING_REQUEST -> "friend.no-pending-request";
            case NOT_FRIENDS -> "friend.not-friends";
            case FRIEND_LIST_FULL -> "friend.friend-list-full";
            case OK -> successKey;
        };
        messages.send(sender, key);
    }

    private void reportTpa(CommandSender sender, FriendTeleportService.ActionResult result, String successKey) {
        if (result == FriendTeleportService.ActionResult.OK) {
            messages.send(sender, successKey);
            return;
        }
        String key = switch (result) {
            case NOT_FRIENDS -> "friend.not-friends";
            case ALREADY_PENDING -> "friend.tpa-already-pending";
            case NO_PENDING_REQUEST -> "friend.tpa-no-pending-request";
            case CANNOT_TARGET_SELF -> "friend.cannot-target-self";
            case OK -> successKey;
        };
        messages.send(sender, key);
    }
}
