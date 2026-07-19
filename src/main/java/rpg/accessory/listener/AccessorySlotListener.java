package rpg.accessory.listener;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import rpg.accessory.manager.AccessorySlotLayout;
import rpg.accessory.model.AccessoryType;
import rpg.accessory.service.AccessoryEffectService;
import rpg.accessory.service.AccessoryIdentityService;
import rpg.core.scheduler.SchedulerService;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces "only the matching accessory type may sit in its designated slot" (SOW section
 * 8) and keeps the status module's equipment contribution in sync with what is actually
 * in that slot. Effects are (re)applied one tick after the inventory event so the click
 * has already been resolved into the slot's new contents.
 */
public final class AccessorySlotListener implements Listener {

    private final AccessoryIdentityService identityService;
    private final AccessoryEffectService effectService;
    private final SchedulerService schedulerService;

    public AccessorySlotListener(AccessoryIdentityService identityService, AccessoryEffectService effectService,
                                  SchedulerService schedulerService) {
        this.identityService = identityService;
        this.effectService = effectService;
        this.schedulerService = schedulerService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        effectService.syncAll(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != InventoryType.PLAYER) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();
        if (!AccessorySlotLayout.isAccessoryRow(slot)) {
            return;
        }

        ItemStack incoming = event.getCursor();
        if (!isAllowed(slot, incoming)) {
            event.setCancelled(true);
            return;
        }

        syncNextTick(player, slot);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Set<Integer> accessorySlots = new HashSet<>();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                continue; // top inventory (chest/GUI), not the player's own storage
            }
            int slot = rawSlot - event.getView().getTopInventory().getSize();
            if (AccessorySlotLayout.isAccessoryRow(slot)) {
                accessorySlots.add(slot);
            }
        }
        if (accessorySlots.isEmpty()) {
            return;
        }
        for (int slot : accessorySlots) {
            if (!isAllowed(slot, event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }
        }
        for (int slot : accessorySlots) {
            syncNextTick(player, slot);
        }
    }

    private boolean isAllowed(int slot, ItemStack incoming) {
        if (incoming == null || incoming.getType().isAir()) {
            return true;
        }
        // A slot in the accessory row with no AccessoryType assigned yet (31-35, "reserved for
        // future accessory types") isn't an accessory slot at all - it's still plain storage
        // until a type is assigned to it, so it must not block arbitrary items.
        Optional<AccessoryType> type = AccessorySlotLayout.typeAtSlot(slot);
        if (type.isEmpty()) {
            return true;
        }
        return identityService.dataOf(incoming).map(data -> data.getType() == type.get()).orElse(false);
    }

    private void syncNextTick(HumanEntity human, int slot) {
        AccessoryType type = AccessorySlotLayout.typeAtSlot(slot).orElse(null);
        if (type == null || !(human instanceof Player player)) {
            return;
        }
        schedulerService.runLater(() -> effectService.applyFromSlot(player, type), 1L);
    }
}
