package rpg.core.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /ol <subcommand> ...} - looks up {@code subcommand} in a {@link PlayerCommandRegistry}
 * and forwards the remaining args to whatever registered it, stripping the subcommand name
 * itself. {@code Command}/{@code label} are forwarded as-is since no registered executor in
 * this codebase reads them.
 */
public final class OlRootCommand implements CommandExecutor, TabCompleter {

    private final OlCommandRegistry registry;

    public OlRootCommand(OlCommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <" + String.join("|", registry.getNames()) + "> ...");
            return true;
        }
        CommandExecutor executor = registry.get(args[0]).orElse(null);
        if (executor == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + args[0]);
            return true;
        }
        return executor.onCommand(sender, command, label + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(registry.getNames(), args.length == 0 ? "" : args[0]);
        }
        CommandExecutor executor = registry.get(args[0]).orElse(null);
        if (executor instanceof TabCompleter completer) {
            return completer.onTabComplete(sender, command, alias + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }
}
