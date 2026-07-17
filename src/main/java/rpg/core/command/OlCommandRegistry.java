package rpg.core.command;

import org.bukkit.command.CommandExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** A registered subcommand plus the metadata {@link CommandHelpUtil} renders in {@code help}. */
    public record Entry(String name, CommandExecutor executor, String description, String usage) {
    }

    private final Map<String, Entry> subcommands = new LinkedHashMap<>();

    public void register(String name, CommandExecutor executor, String description, String usage) {
        subcommands.put(name.toLowerCase(), new Entry(name.toLowerCase(), executor, description, usage));
    }

    public Optional<CommandExecutor> get(String name) {
        Entry entry = subcommands.get(name.toLowerCase());
        return entry == null ? Optional.empty() : Optional.of(entry.executor());
    }

    public Optional<Entry> getEntry(String name) {
        return Optional.ofNullable(subcommands.get(name.toLowerCase()));
    }

    public Set<String> getNames() {
        return Set.copyOf(subcommands.keySet());
    }

    /** Entries in registration order, for help-page rendering. */
    public List<Entry> getEntries() {
        return new ArrayList<>(subcommands.values());
    }
}
