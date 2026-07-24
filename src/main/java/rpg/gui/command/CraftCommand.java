package rpg.gui.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.gui.framework.GuiManager;
import rpg.gui.screen.CraftingGuiScreen;

/**
 * {@code /ol craft} - opens the sender's crafting GUI.
 */
public final class CraftCommand implements CommandExecutor {

    private final GuiManager guiManager;
    private final CraftingGuiScreen craftingGuiScreen;
    private final MessageManager messages;

    public CraftCommand(GuiManager guiManager, CraftingGuiScreen craftingGuiScreen, MessageManager messages) {
        this.guiManager = guiManager;
        this.craftingGuiScreen = craftingGuiScreen;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        guiManager.open(player, craftingGuiScreen.build(player));
        return true;
    }
}
