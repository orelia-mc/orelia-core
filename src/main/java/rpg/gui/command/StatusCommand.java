package rpg.gui.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.gui.framework.GuiManager;
import rpg.gui.screen.StatusGuiScreen;

/**
 * {@code /ol status} - opens the sender's status GUI.
 */
public final class StatusCommand implements CommandExecutor {

    private final GuiManager guiManager;
    private final StatusGuiScreen statusGuiScreen;

    public StatusCommand(GuiManager guiManager, StatusGuiScreen statusGuiScreen) {
        this.guiManager = guiManager;
        this.statusGuiScreen = statusGuiScreen;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        guiManager.open(player, statusGuiScreen.build(player));
        return true;
    }
}
