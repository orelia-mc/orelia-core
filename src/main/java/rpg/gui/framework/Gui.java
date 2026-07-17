package rpg.gui.framework;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import rpg.util.ColorUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * A single custom inventory screen: title, size, and the buttons placed in it. Every
 * concrete screen in {@code rpg.gui.screen} builds one of these instead of touching
 * Bukkit's {@link Inventory} directly, so click routing stays centralized in
 * {@link GuiListener} (SOW coding rule: "GUI処理はGUIパッケージへ集約すること").
 */
public final class Gui {

    private final String title;
    private final int size;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private Inventory inventory;
    private boolean itemMovementAllowed = false;
    private String tag;

    public Gui(String title, int size) {
        this.title = title;
        this.size = size;
    }

    /** Optional marker screens can set so a close listener knows how to react (e.g. "warehouse"). */
    public Gui tag(String tag) {
        this.tag = tag;
        return this;
    }

    public String getTag() {
        return tag;
    }

    /** Warehouse-style screens allow items to be freely moved in/out; button screens don't. */
    public Gui allowItemMovement() {
        this.itemMovementAllowed = true;
        return this;
    }

    public boolean isItemMovementAllowed() {
        return itemMovementAllowed;
    }

    public Gui set(int slot, GuiButton button) {
        buttons.put(slot, button);
        return this;
    }

    public GuiButton getButton(int slot) {
        return buttons.get(slot);
    }

    public Inventory toInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(new GuiHolder(this), size, ColorUtil.component(title));
            buttons.forEach((slot, button) -> inventory.setItem(slot, button.getIcon()));
        }
        return inventory;
    }
}
