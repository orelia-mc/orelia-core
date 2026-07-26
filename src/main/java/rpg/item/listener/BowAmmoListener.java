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
 * Grants a single vanilla arrow when a player draws an Orelia-identified BOW-type weapon
 * (bow/crossbow) with none in inventory - runs at {@link EventPriority#LOWEST} so it happens
 * before vanilla's own "does this player have arrows" check cancels the shot. Combined with
 * {@link rpg.item.service.WeaponFactory}'s hidden Infinity enchant on those weapons, that one
 * arrow is never actually consumed - the bow/crossbow itself is the only "ammo" needed from
 * then on. Does nothing for a plain vanilla bow with no Orelia weapon id, which has no such
 * Infinity enchant and would just run out again after one more shot.
 *
 * <p>This one-time grant can't be avoided entirely with plain Bukkit API: vanilla's own bow-draw
 * check only ever *skips consuming* an arrow the Infinity enchant already found in inventory -
 * it never conjures one from nothing, so a player who somehow has zero arrows (sold/dropped the
 * seed one, or never had it) still can't even start drawing without at least one physically
 * present, and that check happens before any event this class (or any other plugin) can hook -
 * bypassing it entirely would need NMS/reflection, which this codebase otherwise avoids
 * entirely (Paper API + relocated shaded libs only).
 *
 * <p>Rather than fighting the player's 36-slot main inventory for room, the seed arrow goes into
 * the off-hand slot instead whenever it's empty - vanilla's own ammo search already checks the
 * off-hand for arrows (that's the mechanic that lets a player normally carry reserve arrows
 * there), so this is a legitimate ammo location, not a workaround, and it's a slot a bow user
 * essentially never has anything else in. Only if the off-hand is already occupied (a shield,
 * a second weapon, ...) does this fall back to the main inventory, dropping the arrow at the
 * player's feet if even that has no room - Paper's own pickup radius picks it right back up for
 * a stationary player, so the shot still goes through instead of failing with no explanation.
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
        if (hasArrow(inventory)) {
            return;
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType().isAir()) {
            inventory.setItemInOffHand(new ItemStack(Material.ARROW, 1));
            return;
        }
        var leftover = inventory.addItem(new ItemStack(Material.ARROW, 1));
        if (!leftover.isEmpty()) {
            // Off-hand was occupied and the main inventory had no free/stackable slot either -
            // drop at the player's feet rather than silently losing the grant (which would
            // otherwise leave the shot cancelled by vanilla's own arrow check right after this
            // listener returns, with no feedback).
            leftover.values().forEach(stack -> event.getPlayer().getWorld().dropItem(event.getPlayer().getLocation(), stack));
        }
    }

    private boolean hasArrow(PlayerInventory inventory) {
        return inventory.getItemInOffHand().getType() == Material.ARROW || inventory.containsAtLeast(new ItemStack(Material.ARROW), 1);
    }
}
