package rpg.extra.guild.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.Pagination;
import rpg.core.command.TabCompletions;
import rpg.core.config.ConfigManager;
import rpg.core.message.MessageManager;
import rpg.util.ColorUtil;
import rpg.extra.chat.ChatBroadcast;
import rpg.extra.chat.NotificationSoundPlayer;
import rpg.extra.chat.PlayerNameHover;
import rpg.extra.chat.model.ChatBadge;
import rpg.extra.chat.service.ChatMuteService;
import rpg.extra.guild.gui.GuildGuiScreen;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRoleDefinition;
import rpg.extra.guild.service.GuildService;
import rpg.gui.framework.GuiManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * {@code /ol guild create|invite|accept|decline|leave|kick|role|addrole|removerole|renamerole|
 * rename|retag|disband|transfer|list|info|chat} (SOW GuildModule). {@code promote}/{@code demote}
 * were removed with the fixed OFFICER tier - see {@link GuildService}'s own doc comment;
 * {@code role} assigns one of the guild's own freely-named roles instead.
 */
public final class GuildCommand implements CommandExecutor, TabCompleter {

    private static final int LIST_PAGE_SIZE = 15;
    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "decline", "leave", "kick", "role", "addrole", "removerole",
            "renamerole", "rename", "retag", "disband", "transfer", "list", "info", "gui", "chat");
    private static final List<String> MEMBER_TARGET_ACTIONS = List.of("kick", "role", "transfer");

    private final GuildService guildService;
    private final MessageManager messages;
    private final ChatMuteService muteService;
    private final GuildGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final ConfigManager configManager;
    private final Logger logger;

    public GuildCommand(GuildService guildService, MessageManager messages, ChatMuteService muteService,
                         GuildGuiScreen guiScreen, GuiManager guiManager, ConfigManager configManager, Logger logger) {
        this.guildService = guildService;
        this.messages = messages;
        this.muteService = muteService;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
        this.configManager = configManager;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0) {
            openGui(player);
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
            case "rename" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.guild-rename");
                    return true;
                }
                report(sender, guildService.rename(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length))), "guild.renamed");
            }
            case "retag" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.guild-retag");
                    return true;
                }
                report(sender, guildService.retag(player, args[1]), "guild.retagged");
            }
            case "invite" -> withTarget(sender, player, args, target -> {
                GuildService.ActionResult result = guildService.invite(player, target);
                report(sender, result, "guild.invited");
                if (result == GuildService.ActionResult.OK) {
                    target.sendMessage(ColorUtil.componentWithCommand(
                            messages.getPrefix() + messages.format("guild.invite-received", "player", player.getName()),
                            "/guild accept"));
                    // Always plays when config-enabled - a system-driven notification, not a
                    // user-sent chat line, so /chat mute guild (which only mutes actual guild
                    // chat messages) doesn't affect it. See ChatBadge's own doc comment.
                    var config = configManager.get("config.yml").get();
                    NotificationSoundPlayer.play(target, config.getBoolean("guild.notify-sound.enabled", true),
                            config.getString("guild.notify-sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                            config.getDouble("guild.notify-sound.volume", 1.0),
                            config.getDouble("guild.notify-sound.pitch", 1.0), logger);
                }
            });
            case "accept" -> {
                GuildService.ActionResult result = guildService.accept(player, resolveInviteGuild(player, args));
                report(sender, result, "guild.accepted");
                if (result == GuildService.ActionResult.OK) {
                    guildService.getGuild(player.getUniqueId())
                            .ifPresent(guild -> broadcastToGuild(guild, player.getUniqueId(), "guild.member-joined", "player", player.getName()));
                }
            }
            case "decline" -> report(sender, guildService.decline(player, resolveInviteGuild(player, args)), "guild.declined");
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
            case "role" -> {
                if (args.length < 3) {
                    messages.send(sender, "usage.guild-role");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "command.player-not-found", "player", args[1]);
                    return true;
                }
                GuildService.ActionResult result = guildService.assignRole(player, target.getUniqueId(), resolveRoleId(player, args[2]));
                report(sender, result, "guild.role-assigned");
                if (result == GuildService.ActionResult.OK) {
                    guildService.getGuild(player.getUniqueId())
                            .ifPresent(guild -> broadcastToGuild(guild, player.getUniqueId(), "guild.member-role-changed", "player", target.getName()));
                }
            }
            case "addrole" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.guild-addrole");
                    return true;
                }
                report(sender, guildService.addRole(player, args[1]), "guild.role-added");
            }
            case "removerole" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.guild-removerole");
                    return true;
                }
                report(sender, guildService.deleteRole(player, args[1]), "guild.role-removed");
            }
            case "renamerole" -> {
                if (args.length < 3) {
                    messages.send(sender, "usage.guild-renamerole");
                    return true;
                }
                report(sender, guildService.renameRole(player, args[1], args[2]), "guild.role-renamed");
            }
            case "disband" -> {
                Guild guild = guildService.getGuild(player.getUniqueId()).orElse(null);
                GuildService.ActionResult result = guildService.disband(player);
                report(sender, result, "guild.disbanded");
                if (result == GuildService.ActionResult.OK && guild != null) {
                    broadcastToGuild(guild, player.getUniqueId(), "guild.disbanded-notice");
                }
            }
            case "transfer" -> withTarget(sender, player, args, target -> {
                GuildService.ActionResult result = guildService.transferLeadership(player, target.getUniqueId());
                report(sender, result, "guild.leadership-transferred");
                if (result == GuildService.ActionResult.OK) {
                    guildService.getGuild(player.getUniqueId())
                            .ifPresent(guild -> broadcastToGuild(guild, player.getUniqueId(),
                                    "guild.leadership-transferred-notice", "player", target.getName()));
                }
            });
            case "list" -> showList(sender, args);
            case "info" -> showInfo(sender, player);
            case "gui" -> openGui(player);
            // Internal - not in SUBCOMMANDS/usage, invoked only via the ClickEvent showList attaches
            // to each entry (same "hidden runCommand target" convention as orelia-world's
            // /dialoguechoice).
            case "guidetail" -> openGuiDetail(player, args);
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
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("invite")) {
                return TabCompletions.onlinePlayerNames(args[1]);
            }
            if (MEMBER_TARGET_ACTIONS.stream().anyMatch(args[0]::equalsIgnoreCase)) {
                return TabCompletions.matching(onlineMemberNames(player), args[1]);
            }
            if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline")) {
                return TabCompletions.matching(pendingInviteGuildNames(player), args[1]);
            }
            if (args[0].equalsIgnoreCase("removerole") || args[0].equalsIgnoreCase("renamerole")) {
                return TabCompletions.matching(ownGuildRoleNames(player), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("role")) {
            return TabCompletions.matching(ownGuildRoleNames(player), args[2]);
        }
        return List.of();
    }

    /**
     * {@code /guild accept|decline [guildName]} - a guild name (not tag; tags aren't guaranteed
     * unique-enough to type quickly, and the name already is per {@link GuildService#create})
     * identifies "which invite" when more than one is pending at once. With no name, falls back
     * to the oldest pending invite (unchanged single-invite behavior).
     */
    private UUID resolveInviteGuild(Player invitee, String[] args) {
        if (args.length < 2) {
            return guildService.peekPendingInvite(invitee.getUniqueId()).map(Guild::getId).orElse(null);
        }
        for (Guild guild : guildService.peekAllPendingInvites(invitee.getUniqueId())) {
            if (args[1].equalsIgnoreCase(guild.getName())) {
                return guild.getId();
            }
        }
        return null;
    }

    /** Resolves a role name typed on the command line to its internal id, within the actor's own guild - returns the raw text unresolved if no match (so {@code assignRole} reports {@code ROLE_NOT_FOUND} rather than this method masking a typo as "no role"). */
    private String resolveRoleId(Player actor, String roleName) {
        Guild guild = guildService.getGuild(actor.getUniqueId()).orElse(null);
        if (guild == null) {
            return roleName;
        }
        for (GuildRoleDefinition role : guild.getRoles()) {
            if (role.name().equalsIgnoreCase(roleName)) {
                return role.id();
            }
        }
        return roleName;
    }

    private List<String> ownGuildRoleNames(Player player) {
        Guild guild = guildService.getGuild(player.getUniqueId()).orElse(null);
        if (guild == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (GuildRoleDefinition role : guild.getRoles()) {
            names.add(role.name());
        }
        return names;
    }

    private List<String> pendingInviteGuildNames(Player invitee) {
        List<String> names = new ArrayList<>();
        for (Guild guild : guildService.peekAllPendingInvites(invitee.getUniqueId())) {
            names.add(guild.getName());
        }
        return names;
    }

    /** Online guild members' names, excluding {@code viewer} themselves - used for kick/role/transfer tab completion. */
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
            messages.sendRaw(sender, "guild.member-entry", "name", name, "role", guild.roleDisplayName(entry.getValue()));
        }
    }

    /** Opens the viewer's own guild's detail screen if they're in one, otherwise the full guild list. */
    private void openGui(Player player) {
        Guild guild = guildService.getGuild(player.getUniqueId()).orElse(null);
        if (guild != null) {
            guiManager.open(player, guiScreen.buildDetail(player, guild.getId()));
        } else {
            guiManager.open(player, guiScreen.build(player));
        }
    }

    private void openGuiDetail(Player player, String[] args) {
        if (args.length < 2) {
            return;
        }
        try {
            UUID guildId = UUID.fromString(args[1]);
            guiManager.open(player, guiScreen.buildDetail(player, guildId));
        } catch (IllegalArgumentException ignored) {
            // Malformed/stale click target (guild since disbanded) - silently no-op rather
            // than error, same as a stale ClickEvent anywhere else in the plugin.
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
        ChatBroadcast.toGuild(guild, PlayerNameHover.formatLine(messages, "chat.guild-format", player, message),
                ChatBadge.GUILD, muteService);
    }

    private void showList(CommandSender sender, String[] args) {
        int page = args.length >= 2 ? parsePageOrDefault(args[1]) : 1;
        List<Component> lines = new ArrayList<>();
        for (Guild guild : guildService.getAllGuilds()) {
            Component entry = ColorUtil.component(messages.format("guild.list-entry",
                            "tag", guild.getTag(), "name", guild.getName(), "members", guild.getMembers().size()))
                    .clickEvent(ClickEvent.runCommand("/guild guidetail " + guild.getId()))
                    .hoverEvent(HoverEvent.showText(ColorUtil.component("&%7クリックしてGUIで詳細を表示")));
            lines.add(entry);
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
            case NAME_TOO_LONG -> "guild.name-too-long";
            case TAG_TOO_LONG -> "guild.tag-too-long";
            case TARGET_NOT_MEMBER -> "guild.target-not-member";
            case ROLE_NOT_FOUND -> "guild.role-not-found";
            case ROLE_NAME_TAKEN -> "guild.role-name-taken";
            case ROLE_NAME_TOO_LONG -> "guild.role-name-too-long";
            case TOO_MANY_ROLES -> "guild.too-many-roles";
            case ROLE_IN_USE -> "guild.role-in-use";
            case LAST_ROLE -> "guild.last-role";
            case OK -> successKey;
        };
        messages.send(sender, key);
    }
}
