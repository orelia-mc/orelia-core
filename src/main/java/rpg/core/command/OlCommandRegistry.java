package rpg.core.command;

import org.bukkit.command.CommandExecutor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Name-to-{@link CommandExecutor} map backing the shared {@code /ol}/{@code /oladmin}
 * dispatcher commands. orelia-core owns and publishes two instances of this (one for player
 * subcommands, one for admin) via Bukkit's {@code ServicesManager} - see
 * {@link PlayerCommandRegistry}/{@link AdminCommandRegistry} - so orelia-world/orelia-extra
 * can register their own subcommands into the same short entry point instead of each
 * plugin claiming its own top-level command name.
 */
public class OlCommandRegistry {

    private final Map<String, CommandExecutor> subcommands = new LinkedHashMap<>();

    public void register(String name, CommandExecutor executor) {
        subcommands.put(name.toLowerCase(), executor);
    }

    public Optional<CommandExecutor> get(String name) {
        return Optional.ofNullable(subcommands.get(name.toLowerCase()));
    }

    public Set<String> getNames() {
        return Set.copyOf(subcommands.keySet());
    }
}
