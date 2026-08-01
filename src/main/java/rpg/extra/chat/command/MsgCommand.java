package rpg.extra.chat.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /ol msg <player> <message>} (also aliased to top-level {@code /msg}) - a one-off
 * private message, independent of the sender's currently-selected chat channel (see
 * {@code ChatChannelCommand} for that). The friend list's clickable "[メッセージ]" button
 * pre-fills this command via {@code ClickEvent.suggestCommand}.
 */
public final class MsgCommand implements CommandExecutor, TabCompleter {

    private final MessageManager messages;

    public MsgCommand(MessageManager messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "chat.msg-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(sender, "command.player-not-found", "player", args[0]);
            return true;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        messages.send(player, "chat.msg-sent", "player", target.getName(), "message", text);
        messages.send(target, "chat.msg-received", "player", player.getName(), "message", text);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.onlinePlayerNames(args[0]);
        }
        return List.of();
    }
}
