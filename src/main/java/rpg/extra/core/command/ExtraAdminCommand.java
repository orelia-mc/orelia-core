package rpg.extra.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import rpg.core.OreliaPlugin;

/**
 * {@code /oladmin extrareload} - re-reads every orelia-extra config file and asks each
 * module to rebuild its in-memory state. Registered as "extrareload" (not "reload") into
 * orelia-core's shared {@code AdminCommandRegistry} so it doesn't collide with orelia-core's
 * own {@code reload} or orelia-world's {@code worldreload}.
 */
public final class ExtraAdminCommand implements CommandExecutor {

    private final OreliaPlugin plugin;

    public ExtraAdminCommand(OreliaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.reload();
        plugin.getMessageManager().send(sender, "admin.reloaded");
        return true;
    }
}
