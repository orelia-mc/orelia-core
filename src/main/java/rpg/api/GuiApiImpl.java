package rpg.api;

import org.bukkit.entity.Player;
import rpg.gui.GuiModule;
import rpg.gui.framework.GuiManager;

import java.util.List;

final class GuiApiImpl implements GuiApi {

    private final GuiModule guiModule;
    private final GuiManager guiManager;

    GuiApiImpl(GuiModule guiModule) {
        this.guiModule = guiModule;
        this.guiManager = guiModule.getGuiManager();
    }

    @Override
    public void openStatus(Player player) {
        guiManager.open(player, guiModule.getStatusGuiScreen().build(player));
    }

    @Override
    public void openEquipment(Player player) {
        guiManager.open(player, guiModule.getEquipmentGuiScreen().build(player));
    }

    @Override
    public void openSkill(Player player) {
        guiManager.open(player, guiModule.getSkillGuiScreen().build(player));
    }

    @Override
    public void openJobChange(Player player) {
        guiManager.open(player, guiModule.getJobGuiScreen().build(player));
    }

    @Override
    public void openWarehouse(Player player) {
        guiManager.open(player, guiModule.getWarehouseGuiScreen().build(player));
    }

    @Override
    public void openShop(Player player, List<ShopEntry> stock) {
        guiManager.open(player, guiModule.getShopGuiScreen().build(player, stock));
    }

    @Override
    public void openCrafting(Player player) {
        guiManager.open(player, guiModule.getCraftingGuiScreen().build(player));
    }
}
