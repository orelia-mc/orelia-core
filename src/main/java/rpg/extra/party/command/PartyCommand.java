package rpg.extra.party.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.chat.ChatBroadcast;
import rpg.extra.party.model.Party;
import rpg.extra.party.service.PartyService;
import rpg.util.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /ol party create|invite|accept|decline|leave|kick|disband|transfer|list|chat} (SOW PartyModule).
 */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "decline", "leave", "kick", "disband", "transfer", "list", "chat");
    private static final List<String> MEMBER_TARGET_ACTIONS = List.of("kick", "transfer");

    private final PartyService partyService;
    private final MessageManager messages;

    public PartyCommand(PartyService partyService, MessageManager messages) {
        this.partyService = partyService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage.party");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> report(sender, partyService.create(player), "party.created");
            case "invite" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.party-invite");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "command.player-not-found", "player", args[1]);
                    return true;
                }
                PartyService.ActionResult result = partyService.invite(player, target);
                report(sender, result, "party.invited");
                if (result == PartyService.ActionResult.OK) {
                    sendInviteNotification(target, player);
                }
            }
            case "accept" -> {
                PartyService.ActionResult result = partyService.accept(player);
                report(sender, result, "party.accepted");
                if (result == PartyService.ActionResult.OK) {
                    partyService.getParty(player.getUniqueId())
                            .ifPresent(party -> broadcastToParty(party, player.getUniqueId(), "party.member-joined", "player", player.getName()));
                }
            }
            case "decline" -> report(sender, partyService.decline(player), "party.declined");
            case "leave" -> {
                Party party = partyService.getParty(player.getUniqueId()).orElse(null);
                PartyService.ActionResult result = partyService.leave(player);
                report(sender, result, "party.left");
                if (result == PartyService.ActionResult.OK && party != null) {
                    broadcastToParty(party, player.getUniqueId(), "party.member-left", "player", player.getName());
                }
            }
            case "kick" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.party-kick");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "command.player-not-found", "player", args[1]);
                    return true;
                }
                PartyService.ActionResult result = partyService.kick(player, target.getUniqueId());
                report(sender, result, "party.kicked");
                if (result == PartyService.ActionResult.OK) {
                    messages.send(target, "party.kicked-notice");
                    partyService.getParty(player.getUniqueId())
                            .ifPresent(party -> broadcastToParty(party, player.getUniqueId(), "party.member-left", "player", target.getName()));
                }
            }
            case "disband" -> {
                Party party = partyService.getParty(player.getUniqueId()).orElse(null);
                PartyService.ActionResult result = partyService.disband(player);
                report(sender, result, "party.disbanded");
                if (result == PartyService.ActionResult.OK && party != null) {
                    broadcastToParty(party, player.getUniqueId(), "party.disbanded-notice");
                }
            }
            case "transfer" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.party-transfer");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "command.player-not-found", "player", args[1]);
                    return true;
                }
                report(sender, partyService.transferLeadership(player, target.getUniqueId()), "party.leadership-transferred");
            }
            case "list" -> listMembers(sender, player);
            case "chat" -> partyChat(sender, player, args);
            default -> messages.send(sender, "usage.party");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length != 2 || !(sender instanceof Player player)) {
            return List.of();
        }
        if (args[0].equalsIgnoreCase("invite")) {
            return TabCompletions.onlinePlayerNames(args[1]);
        }
        if (MEMBER_TARGET_ACTIONS.stream().anyMatch(args[0]::equalsIgnoreCase)) {
            return TabCompletions.matching(onlineMemberNames(player), args[1]);
        }
        return List.of();
    }

    /** Online party members' names, excluding {@code viewer} themselves - used for the "kick"/"transfer" tab completion. */
    private List<String> onlineMemberNames(Player viewer) {
        Party party = partyService.getParty(viewer.getUniqueId()).orElse(null);
        if (party == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID memberId : party.getMembers()) {
            if (memberId.equals(viewer.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                names.add(member.getName());
            }
        }
        return names;
    }

    /** Sends the invite text plus clickable 承認/拒否 buttons (SOW: party invite click-to-respond). */
    private void sendInviteNotification(Player invitee, Player inviter) {
        messages.send(invitee, "party.invite-received", "player", inviter.getName());
        Component accept = ColorUtil.component(messages.format("party.invite-accept-button"))
                .clickEvent(ClickEvent.runCommand("/ol party accept"))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(messages.format("party.invite-accept-hover"))));
        Component decline = ColorUtil.component(messages.format("party.invite-decline-button"))
                .clickEvent(ClickEvent.runCommand("/ol party decline"))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(messages.format("party.invite-decline-hover"))));
        invitee.sendMessage(Component.text(" ").append(accept).append(Component.text("   ")).append(decline));
    }

    private void partyChat(CommandSender sender, Player player, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "chat.usage-party-chat");
            return;
        }
        Party party = partyService.getParty(player.getUniqueId()).orElse(null);
        if (party == null) {
            messages.send(sender, "chat.not-in-party");
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ChatBroadcast.toParty(party, ColorUtil.component(
                messages.format("chat.party-format", "sender", player.getName(), "message", message)));
    }

    private void listMembers(CommandSender sender, Player player) {
        Party party = partyService.getParty(player.getUniqueId()).orElse(null);
        if (party == null) {
            messages.send(sender, "party.not-in-party");
            return;
        }
        messages.send(sender, "party.members-header");
        for (var memberId : party.getMembers()) {
            String name = Bukkit.getOfflinePlayer(memberId).getName();
            boolean isLeader = memberId.equals(party.getLeaderId());
            String leaderTag = isLeader ? messages.format("party.member-leader-tag") : "";
            messages.sendRaw(sender, "party.member-entry", "name", name, "leader", leaderTag);
        }
    }

    /** Announces a party event to every online member except {@code exclude} (typically the actor, who already got their own result message). */
    private void broadcastToParty(Party party, UUID exclude, String key, Object... placeholders) {
        for (UUID memberId : party.getMembers()) {
            if (memberId.equals(exclude)) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                messages.send(member, key, placeholders);
            }
        }
    }

    private void report(CommandSender sender, PartyService.ActionResult result, String successKey) {
        if (result == PartyService.ActionResult.OK) {
            messages.send(sender, successKey);
            return;
        }
        String key = switch (result) {
            case ALREADY_IN_PARTY -> "party.already-in-party";
            case NOT_IN_PARTY -> "party.not-in-party";
            case NOT_LEADER -> "party.not-leader";
            case PARTY_FULL -> "party.party-full";
            case TARGET_ALREADY_IN_PARTY -> "party.target-already-in-party";
            case NO_PENDING_INVITE -> "party.no-pending-invite";
            case CANNOT_TARGET_SELF -> "party.cannot-target-self";
            case LEADER_MUST_DISBAND -> "party.leader-must-disband";
            case OK -> successKey;
        };
        messages.send(sender, key);
    }
}
