package rpg.relic.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiManager;
import rpg.relic.gui.RelicUpgradeGuiScreen;

/** {@code /ol relic upgrade} - opens the "選べる厳選" GUI for the relic held in the main hand. */
public final class RelicCommand implements CommandExecutor {

    private final RelicUpgradeGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public RelicCommand(RelicUpgradeGuiScreen guiScreen, GuiManager guiManager, MessageManager messages) {
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("upgrade")) {
            messages.send(player, "relic.usage");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        Gui gui = guiScreen.build(player, held).orElse(null);
        if (gui == null) {
            messages.send(player, "relic.not-holding-relic");
            return true;
        }
        guiManager.open(player, gui);
        return true;
    }
}
