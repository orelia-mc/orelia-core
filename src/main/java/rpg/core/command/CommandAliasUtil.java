package rpg.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Registers a standalone top-level Bukkit command (e.g. {@code /guild}) at runtime, without
 * declaring it in {@code plugin.yml}, that simply delegates to an already-built
 * {@link CommandExecutor}/{@link TabCompleter}. Used so a subcommand already registered into
 * {@link PlayerCommandRegistry} (e.g. {@code /ol guild}) can also be run directly as
 * {@code /guild} - both paths share the exact same executor instance and state.
 *
 * <p>{@link Plugin#getServer()}{@code .getCommandMap()} (via {@link org.bukkit.Server}) is the
 * stable public API for this - unlike {@code SimplePluginManager}, whose {@code CommandMap}
 * field is private with no getter. {@link CommandMap#register(String, Command)} namespaces the
 * command under {@code fallbackPrefix} on a name collision rather than failing, so registering
 * an alias here is always safe.
 */
public final class CommandAliasUtil {

    private CommandAliasUtil() {
    }

    public static void registerAlias(Plugin plugin, String name, CommandExecutor executor, String description, String usage) {
        CommandMap commandMap = plugin.getServer().getCommandMap();
        String usageString = "/" + name + (usage == null || usage.isBlank() ? "" : " " + usage);
        Command command = new Command(name, description == null ? "" : description, usageString, List.of()) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return executor.onCommand(sender, this, label, args);
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (executor instanceof TabCompleter completer) {
                    List<String> completions = completer.onTabComplete(sender, this, alias, args);
                    return completions == null ? List.of() : completions;
                }
                return List.of();
            }
        };
        commandMap.register(plugin.getName().toLowerCase(), command);
    }
}
