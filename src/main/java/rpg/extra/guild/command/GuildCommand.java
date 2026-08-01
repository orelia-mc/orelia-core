package rpg.extra.guild.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.Pagination;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.util.ColorUtil;
import rpg.extra.chat.ChatBroadcast;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRole;
import rpg.extra.guild.service.GuildService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /ol guild create|invite|accept|leave|kick|promote|demote|disband|transfer|list|info|chat}
 * (SOW GuildModule).
 */
public final class GuildCommand implements CommandExecutor, TabCompleter {

    private static final int LIST_PAGE_SIZE = 15;
    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "leave", "kick", "promote", "demote", "disband", "transfer", "list", "info", "chat");
    private static final List<String> MEMBER_TARGET_ACTIONS = List.of("kick", "promote", "demote", "transfer");

    private final GuildService guildService;
    private final MessageManager messages;

    public GuildCommand(GuildService guildService, MessageManager messages) {
        this.guildService = guildService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage.guild");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    messages.send(sender, "usage.guild-create");
                    return true;
                }
                report(sender, guildService.create(player, args[1], args[2]), "guild.created");
            }
            case "invite" -> withTarget(sender, player, args, target -> {
                GuildService.ActionResult result = guildService.invite(player, target);
                report(sender, result, "guild.invited");
                if (result == GuildService.ActionResult.OK) {
                    target.sendMessage(ColorUtil.componentWithCommand(
                            messages.getPrefix() + messages.format("guild.invite-received", "player", player.getName()),
                            "/guild accept"));
                }
            });
            case "accept" -> {
                GuildService.ActionResult result = guildService.accept(player);
                report(sender, result, "guild.accepted");
                if (result == GuildService.ActionResult.OK) {
                    guildService.getGuild(player.getUniqueId())
                            .ifPresent(guild -> broadcastToGuild(guild, player.getUniqueId(), "guild.member-joined", "player", player.getName()));
                }
            }
            case "leave" -> {
                Guild guild = guildService.getGuild(player.getUniqueId()).orElse(null);
                GuildService.ActionResult result = guildService.leave(player);
                report(sender, result, "guild.left");
                if (result == GuildService.ActionResult.OK && guild != null) {
                    broadcastToGuild(guild, player.getUniqueId(), "guild.member-left", "player", player.getName());
                }
            }
            case "kick" -> withTarget(sender, player, args, target -> {
                GuildService.ActionResult result = guildService.kick(player, target.getUniqueId());
                report(sender, result, "guild.kicked");
                if (result == GuildService.ActionResult.OK) {
                    messages.send(target, "guild.kicked-notice");
                    guildService.getGuild(player.getUniqueId())
                            .ifPresent(guild -> broadcastToGuild(guild, player.getUniqueId(), "guild.member-left", "player", target.getName()));
                }
            });
            case "promote" -> withTarget(sender, player, args, target ->
                    report(sender, guildService.setRole(player, target.getUniqueId(), GuildRole.OFFICER), "guild.promoted"));
            case "demote" -> withTarget(sender, player, args, target ->
                    report(sender, guildService.setRole(player, target.getUniqueId(), GuildRole.MEMBER), "guild.demoted"));
            case "disband" -> {
                Guild guild = guildService.getGuild(player.getUniqueId()).orElse(null);
                GuildService.ActionResult result = guildService.disband(player);
                report(sender, result, "guild.disbanded");
                if (result == GuildService.ActionResult.OK && guild != null) {
                    broadcastToGuild(guild, player.getUniqueId(), "guild.disbanded-notice");
                }
            }
            case "transfer" -> withTarget(sender, player, args, target ->
                    report(sender, guildService.transferLeadership(player, target.getUniqueId()), "guild.leadership-transferred"));
            case "list" -> showList(sender, args);
            case "info" -> showInfo(sender, player);
            case "chat" -> guildChat(sender, player, args);
            default -> messages.send(sender, "usage.guild");
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

    /** Online guild members' names, excluding {@code viewer} themselves - used for kick/promote/demote/transfer tab completion. */
    private List<String> onlineMemberNames(Player viewer) {
        Guild guild = guildService.getGuild(viewer.getUniqueId()).orElse(null);
        if (guild == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID memberId : guild.getMembers().keySet()) {
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

    private void withTarget(CommandSender sender, Player player, String[] args, java.util.function.Consumer<Player> action) {
        if (args.length < 2) {
            messages.send(sender, "usage.guild-target", "action", args[0]);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "command.player-not-found", "player", args[1]);
            return;
        }
        action.accept(target);
    }

    private void showInfo(CommandSender sender, Player player) {
        Guild guild = guildService.getGuild(player.getUniqueId()).orElse(null);
        if (guild == null) {
            messages.send(sender, "guild.not-in-guild");
            return;
        }
        messages.sendRaw(sender, "guild.info-header", "tag", guild.getTag(), "name", guild.getName());
        for (var entry : guild.getMembers().entrySet()) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            messages.sendRaw(sender, "guild.member-entry", "name", name, "role", entry.getValue().getDisplayName());
        }
    }

    private void guildChat(CommandSender sender, Player player, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "chat.usage-guild-chat");
            return;
        }
        Guild guild = guildService.getGuild(player.getUniqueId()).orElse(null);
        if (guild == null) {
            messages.send(sender, "chat.not-in-guild");
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ChatBroadcast.toGuild(guild, ColorUtil.component(
                messages.format("chat.guild-format", "sender", player.getName(), "message", message)));
    }

    private void showList(CommandSender sender, String[] args) {
        int page = args.length >= 2 ? parsePageOrDefault(args[1]) : 1;
        List<Component> lines = new ArrayList<>();
        for (Guild guild : guildService.getAllGuilds()) {
            lines.add(ColorUtil.component(messages.format("guild.list-entry",
                    "tag", guild.getTag(), "name", guild.getName(), "members", guild.getMembers().size())));
        }
        Pagination.send(sender, "&%6&lギルド一覧&%7 ({page}/{total}ページ)", lines, LIST_PAGE_SIZE, page,
                "/guild list", "&%7登録されているギルドはありません。");
    }

    private int parsePageOrDefault(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Announces a guild event to every online member except {@code exclude} (typically the actor, who already got their own result message). */
    private void broadcastToGuild(Guild guild, UUID exclude, String key, Object... placeholders) {
        for (UUID memberId : guild.getMembers().keySet()) {
            if (memberId.equals(exclude)) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                messages.send(member, key, placeholders);
            }
        }
    }

    private void report(CommandSender sender, GuildService.ActionResult result, String successKey) {
        if (result == GuildService.ActionResult.OK) {
            messages.send(sender, successKey);
            return;
        }
        String key = switch (result) {
            case ALREADY_IN_GUILD -> "guild.already-in-guild";
            case NOT_IN_GUILD -> "guild.not-in-guild";
            case INSUFFICIENT_ROLE -> "guild.insufficient-role";
            case TARGET_ALREADY_IN_GUILD -> "guild.target-already-in-guild";
            case NO_PENDING_INVITE -> "guild.no-pending-invite";
            case CANNOT_TARGET_SELF -> "guild.cannot-target-self";
            case CANNOT_TARGET_LEADER -> "guild.cannot-target-leader";
            case LEADER_MUST_DISBAND -> "guild.leader-must-disband";
            case NAME_TAKEN -> "guild.name-taken";
            case TAG_TAKEN -> "guild.tag-taken";
            case TARGET_NOT_MEMBER -> "guild.target-not-member";
            case OK -> successKey;
        };
        messages.send(sender, key);
    }
}
