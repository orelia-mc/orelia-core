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
            // "/ol help <subcommand>" shows just that one entry instead of the full paginated
            // listing - args[1] is only treated as a page number once it doesn't match a
            // registered subcommand name (so "/ol help 2" still pages as before).
            if (args.length >= 2) {
                var single = registry.getEntry(args[1]);
                if (single.isPresent()) {
                    CommandHelpUtil.sendHelp(sender, label, List.of(single.get()), 1);
                    return true;
                }
            }
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
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return TabCompletions.matching(registry.getNames(), args[1]);
        }
        CommandExecutor executor = registry.get(args[0]).orElse(null);
        if (executor instanceof TabCompleter completer) {
            return completer.onTabComplete(sender, command, alias + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }
}
