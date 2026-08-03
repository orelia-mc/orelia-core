package rpg.extra.chat.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import rpg.core.message.MessageManager;
import rpg.extra.chat.ChatBroadcast;
import rpg.util.ColorUtil;

/**
 * {@code /oladmin chat <message>} - one-off admin-chat broadcast; does not change the
 * sender's currently-selected chat channel (see {@code ChatChannelCommand} for that).
 */
public final class AdminChatCommand implements CommandExecutor {

    private final MessageManager messages;

    public AdminChatCommand(MessageManager messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            messages.send(sender, "chat.usage-admin-chat");
            return true;
        }
        String message = String.join(" ", args);
        ChatBroadcast.toAdmins(ColorUtil.component(
                messages.format("chat.admin-format", "sender", sender.getName(), "message", message)));
        return true;
    }
}
