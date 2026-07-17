package rpg.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import rpg.util.ColorUtil;

import java.util.ArrayList;
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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            int page = args.length >= 2 ? parsePageOrDefault(args[1]) : 1;
            CommandHelpUtil.sendHelp(sender, label, registry.getEntries(), page);
            return true;
        }
        CommandExecutor executor = registry.get(args[0]).orElse(null);
        if (executor == null) {
            sender.sendMessage(ColorUtil.component("&cUnknown subcommand: " + args[0] + ". Try /" + label + " help."));
            return true;
        }
        return executor.onCommand(sender, command, label + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
    }

    private int parsePageOrDefault(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<>(registry.getNames());
            options.add("help");
            return TabCompletions.matching(options, args.length == 0 ? "" : args[0]);
        }
        CommandExecutor executor = registry.get(args[0]).orElse(null);
        if (executor instanceof TabCompleter completer) {
            return completer.onTabComplete(sender, command, alias + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }
}
