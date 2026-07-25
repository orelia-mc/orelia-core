package rpg.item.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;

/**
 * Silently grants a single vanilla arrow when a player draws an Orelia-identified BOW-type
 * weapon (bow/crossbow) with none in inventory - runs at {@link EventPriority#LOWEST} so it
 * happens before vanilla's own "does this player have arrows" check cancels the shot. Combined
 * with {@link rpg.item.service.WeaponFactory}'s hidden Infinity enchant on those weapons, that
 * one arrow is never actually consumed - the bow/crossbow itself is the only "ammo" needed
 * from then on. Does nothing for a plain vanilla bow with no Orelia weapon id, which has no
 * such Infinity enchant and would just run out again after one more shot.
 */
public final class BowAmmoListener implements Listener {

    private final WeaponIdentityService identityService;

    public BowAmmoListener(WeaponIdentityService identityService) {
        this.identityService = identityService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        WeaponData data = identityService.dataOf(item).orElse(null);
        if (data == null || data.getWeaponType() != WeaponType.BOW) {
            return;
        }
        PlayerInventory inventory = event.getPlayer().getInventory();
        if (!inventory.containsAtLeast(new ItemStack(Material.ARROW), 1)) {
            inventory.addItem(new ItemStack(Material.ARROW, 1));
        }
    }
}
