package rpg.gui.framework;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * The single click/drag handler for every {@link Gui} screen in the plugin. A button-only
 * screen (the common case - display/action-button, not storage) locks down the *top* (GUI)
 * inventory, except any slot marked {@link Gui#interactiveSlot(int)}; the player's *own* bottom
 * inventory stays freely usable except for a shift-click, which would otherwise dump an item
 * into the locked-down top half. A {@link Gui#allowItemMovement()} screen (e.g. warehouse) skips
 * all of this and behaves like plain storage.
 */
public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        Gui gui = holder.getGui();
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!gui.isItemMovementAllowed()) {
            if (clickedTop) {
                if (!gui.getInteractiveSlots().contains(event.getSlot())) {
                    event.setCancelled(true);
                }
            } else if (event.getClick().isShiftClick()) {
                // A shift-click from the player's own inventory tries to dump the item into the
                // top (locked-down) inventory - block only that, not ordinary bottom-inventory use.
                event.setCancelled(true);
            }
        }

        if (!clickedTop || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GuiButton button = gui.getButton(event.getSlot());
        if (button != null) {
            button.getAction().onClick(player, event.getClick().name());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        Gui gui = holder.getGui();
        if (gui.isItemMovementAllowed()) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !gui.getInteractiveSlots().contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
