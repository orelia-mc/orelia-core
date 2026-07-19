package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.accessory.manager.AccessorySlotLayout;
import rpg.accessory.model.AccessoryType;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.util.ItemBuilder;

/**
 * Read-only view of the currently held weapon and equipped accessories (SOW section 17
 * "装備"). Actual equip/unequip still happens in the player's own inventory.
 */
public final class EquipmentGuiScreen {

    private final GuiConfig guiConfig;

    public EquipmentGuiScreen(GuiConfig guiConfig) {
        this.guiConfig = guiConfig;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("equipment", "&%8装備"), 27);

        ItemStack weapon = player.getInventory().getItemInMainHand();
        gui.set(13, GuiButton.display(weapon.getType().isAir()
                ? new ItemBuilder(Material.BARRIER).name("&%7武器なし").build()
                : weapon.clone()));

        int[] slots = {19, 20, 21, 22};
        AccessoryType[] types = AccessoryType.values();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < types.length; i++) {
            ItemStack accessory = storage[AccessorySlotLayout.slotFor(types[i])];
            gui.set(slots[i], GuiButton.display(accessory == null || accessory.getType().isAir()
                    ? new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&%7" + types[i]).build()
                    : accessory.clone()));
        }
        return gui;
    }
}
