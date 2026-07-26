package rpg.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiManager;
import rpg.relic.gui.RelicUpgradeGuiScreen;
import rpg.relic.service.RelicGenerationService;

import java.util.Optional;

final class RelicApiImpl implements RelicApi {

    private final RelicGenerationService generationService;
    private final RelicUpgradeGuiScreen upgradeGuiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    RelicApiImpl(RelicGenerationService generationService, RelicUpgradeGuiScreen upgradeGuiScreen,
                 GuiManager guiManager, MessageManager messages) {
        this.generationService = generationService;
        this.upgradeGuiScreen = upgradeGuiScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    @Override
    public Optional<ItemStack> generateRelic(String sourceDungeonId) {
        return generationService.generate(sourceDungeonId);
    }

    @Override
    public void openUpgradeGui(Player player) {
        Optional<Gui> gui = upgradeGuiScreen.build(player, player.getInventory().getItemInMainHand());
        if (gui.isEmpty()) {
            messages.send(player, "relic.not-holding-relic");
            return;
        }
        guiManager.open(player, gui.get());
    }
}
