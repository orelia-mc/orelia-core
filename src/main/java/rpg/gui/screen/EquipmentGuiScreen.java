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
 * "装備"). Actual equip/unequip still happens in the player's own inventory - since that
 * inventory stays open/interactive underneath this screen (chest-type GUI), swapping the
 * held weapon or an accessory slot while this screen is open would otherwise leave the
 * display stale until closed and reopened; {@link rpg.gui.listener.EquipmentDisplayRefreshListener}
 * (tag {@link #TAG}) keeps it live instead.
 */
public final class EquipmentGuiScreen {

    public static final String TAG = "equipment";

    private static final int WEAPON_SLOT = 13;
    private static final int[] ACCESSORY_SLOTS = {19, 20, 21, 22};

    private final GuiConfig guiConfig;

    public EquipmentGuiScreen(GuiConfig guiConfig) {
        this.guiConfig = guiConfig;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("equipment", "&%8装備"), 27).tag(TAG);
        gui.set(WEAPON_SLOT, GuiButton.display(weaponIcon(player)));
        AccessoryType[] types = AccessoryType.values();
        for (int i = 0; i < types.length; i++) {
            gui.set(ACCESSORY_SLOTS[i], GuiButton.display(accessoryIcon(player, types[i])));
        }
        return gui;
    }

    /** Re-renders the weapon/accessory icons into {@code inventory} without rebuilding the whole {@link Gui}. */
    public void refresh(Player player, org.bukkit.inventory.Inventory inventory) {
        inventory.setItem(WEAPON_SLOT, weaponIcon(player));
        AccessoryType[] types = AccessoryType.values();
        for (int i = 0; i < types.length; i++) {
            inventory.setItem(ACCESSORY_SLOTS[i], accessoryIcon(player, types[i]));
        }
    }

    private ItemStack weaponIcon(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        return weapon.getType().isAir()
                ? new ItemBuilder(Material.BARRIER).name("&%7武器なし").build()
                : weapon.clone();
    }

    private ItemStack accessoryIcon(Player player, AccessoryType type) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack accessory = storage[AccessorySlotLayout.slotFor(type)];
        return accessory == null || accessory.getType().isAir()
                ? new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&%7" + type.getDisplayName()).build()
                : accessory.clone();
    }
}
