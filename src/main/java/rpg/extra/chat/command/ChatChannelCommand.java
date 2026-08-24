package rpg.extra.chat.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.chat.model.ChatBadge;
import rpg.extra.chat.model.ChatChannel;
import rpg.extra.chat.service.ChatChannelService;
import rpg.extra.chat.service.ChatMuteService;

import java.util.List;
import java.util.Set;

/**
 * {@code /ol chat [public|party|guild|admin]} - switches the sender's default chat channel;
 * with no argument, reports the currently-selected one instead. Also handles the sibling
 * {@code /ol chat mute [category]} subcommand (toggle a {@link ChatBadge} category's mute state,
 * or list currently-muted categories with no argument) - folded into this command rather than
 * registered separately since it's the same {@code /ol chat ...} entry point.
 */
public final class ChatChannelCommand implements CommandExecutor, TabCompleter {

    private static final List<String> FIRST_ARG_OPTIONS = List.of("public", "party", "guild", "admin", "mute");
    private static final List<String> CATEGORY_NAMES = List.of("public", "party", "guild");

    private final ChatChannelService channelService;
    private final ChatMuteService muteService;
    private final MessageManager messages;

    public ChatChannelCommand(ChatChannelService channelService, ChatMuteService muteService, MessageManager messages) {
        this.channelService = channelService;
        this.muteService = muteService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("mute")) {
            handleMute(player, args);
            return true;
        }
        if (args.length < 1) {
            messages.send(player, "chat.current-channel", "channel", channelService.getChannel(player.getUniqueId()).getDisplayName());
            return true;
        }
        ChatChannel channel;
        try {
            channel = ChatChannel.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            messages.send(sender, "chat.invalid-channel", "channel", args[0]);
            return true;
        }
        ChatChannelService.SwitchResult result = channelService.switchChannel(player, channel);
        switch (result) {
            case OK -> messages.send(player, "chat.switched", "channel", channel.getDisplayName());
            case PARTY_REQUIRED -> messages.send(player, "chat.party-required");
            case GUILD_REQUIRED -> messages.send(player, "chat.guild-required");
            case ADMIN_PERMISSION_REQUIRED -> messages.send(player, "chat.admin-permission-required");
        }
        return true;
    }

    private void handleMute(Player player, String[] args) {
        if (!muteService.isEnabled()) {
            messages.send(player, "chat.mute-disabled");
            return;
        }
        if (args.length < 2) {
            Set<ChatBadge> muted = muteService.getMuted(player.getUniqueId());
            if (muted.isEmpty()) {
                messages.send(player, "chat.mute-status-empty");
                return;
            }
            messages.send(player, "chat.mute-status-header");
            for (ChatBadge category : muted) {
                messages.send(player, "chat.mute-status-entry", "category", category.getDisplayName());
            }
            return;
        }
        ChatBadge category;
        try {
            category = ChatBadge.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            messages.send(player, "chat.mute-invalid-category", "category", args[1]);
            return;
        }
        boolean nowMuted = muteService.toggle(player.getUniqueId(), category);
        messages.send(player, nowMuted ? "chat.mute-on" : "chat.mute-off", "category", category.getDisplayName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(FIRST_ARG_OPTIONS, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mute")) {
            return TabCompletions.matching(CATEGORY_NAMES, args[1]);
        }
        return List.of();
    }
}
