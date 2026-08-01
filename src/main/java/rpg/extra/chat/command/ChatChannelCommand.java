package rpg.extra.chat.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.chat.model.ChatChannel;
import rpg.extra.chat.service.ChatChannelService;

import java.util.List;

/**
 * {@code /ol chat [public|party|guild|admin]} - switches the sender's default chat channel;
 * with no argument, reports the currently-selected one instead.
 */
public final class ChatChannelCommand implements CommandExecutor, TabCompleter {

    private static final List<String> CHANNEL_NAMES = List.of("public", "party", "guild", "admin");

    private final ChatChannelService channelService;
    private final MessageManager messages;

    public ChatChannelCommand(ChatChannelService channelService, MessageManager messages) {
        this.channelService = channelService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(CHANNEL_NAMES, args.length == 0 ? "" : args[0]);
        }
        return List.of();
    }
}
