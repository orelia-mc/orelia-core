package rpg.gui.listener;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import rpg.core.scheduler.SchedulerService;
import rpg.gui.framework.GuiHolder;
import rpg.gui.screen.EquipmentGuiScreen;

/**
 * {@link EquipmentGuiScreen} shows the player's own held weapon and accessory slots, which
 * stay live/interactive underneath it (it's a chest-type GUI, not a full-screen takeover) - a
 * hotbar swap (number key/scroll) or an accessory-slot click in the player's own inventory
 * while this screen is open would otherwise leave the displayed icons stale until the player
 * closes and reopens it. Refreshes the open GUI in place instead, one tick later (same reason
 * {@code AccessorySlotListener} does - the click/held-slot change needs to have already
 * resolved into the player's inventory before re-reading it).
 */
public final class EquipmentDisplayRefreshListener implements Listener {

    private final EquipmentGuiScreen equipmentGuiScreen;
    private final SchedulerService schedulerService;

    public EquipmentDisplayRefreshListener(EquipmentGuiScreen equipmentGuiScreen, SchedulerService schedulerService) {
        this.equipmentGuiScreen = equipmentGuiScreen;
        this.schedulerService = schedulerService;
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        refreshIfOpen(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        refreshIfOpen(event.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        refreshIfOpen(event.getWhoClicked());
    }

    private void refreshIfOpen(HumanEntity human) {
        if (!(human instanceof Player player)) {
            return;
        }
        schedulerService.runLater(() -> {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof GuiHolder holder && EquipmentGuiScreen.TAG.equals(holder.getGui().getTag())) {
                equipmentGuiScreen.refresh(player, top);
            }
        }, 1L);
    }
}
