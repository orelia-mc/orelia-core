package rpg.world.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import rpg.core.OreliaPlugin;

/**
 * {@code /oladmin worldreload} - re-reads every orelia-world config file and asks each
 * module to rebuild its in-memory state. Registered as "worldreload" (not "reload") into
 * orelia-core's shared {@code AdminCommandRegistry} so it doesn't collide with orelia-core's
 * own {@code reload}.
 */
public final class WorldAdminCommand implements CommandExecutor {

    private final OreliaPlugin plugin;

    public WorldAdminCommand(OreliaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.reload();
        plugin.getMessageManager().send(sender, "admin.reloaded");
        return true;
    }
}
