package rpg.world.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import rpg.core.OreliaPlugin;

/**
 * {@code /oladmin worldreload} - alias of {@code /oladmin reload} kept for one release cycle
 * so admin muscle-memory/scripts from the pre-merge 3-plugin setup keep working. Reloads
 * every module's config, not just the former orelia-world ones - since the merge there is
 * only one {@link OreliaPlugin#reload()}.
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
