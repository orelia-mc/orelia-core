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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;

/**
 * Seeds a single vanilla arrow, worn in the leggings slot, the first time a player draws an
 * Orelia-identified BOW-type weapon (bow/crossbow) with none present - runs at
 * {@link EventPriority#LOWEST} so it happens before vanilla's own "does this player have
 * arrows" check cancels the draw. Combined with {@link rpg.item.service.WeaponFactory}'s hidden
 * Infinity enchant on those weapons, that one arrow is never actually consumed - the
 * bow/crossbow itself is the only "ammo" needed from then on. Does nothing for a plain vanilla
 * bow with no Orelia weapon id, which has no such Infinity enchant and would just run out again
 * after one more shot.
 *
 * <p>The seeded arrow carries a {@code minecraft:equippable} component (slot {@code LEGS}) and
 * is placed directly via {@link PlayerInventory#setLeggings}, rather than sitting as a normal
 * inventory item - vanilla's own ammo search scans the player's full 41-slot container
 * (hotbar/storage, armor, and off-hand alike, confirmed against Paper's own decompiled
 * {@code Player#getProjectile} source), so an arrow worn in the leggings slot satisfies the
 * "has ammo" check exactly the same as one sitting loose in a backpack slot would. Since real
 * armor is banned outright (see {@code rpg.status.listener.ArmorBanListener}), the leggings
 * slot is guaranteed to always be free for this - unlike the general 36-slot inventory (which
 * can fill up with loot) or the off-hand (which a shield or second weapon can occupy), this
 * reserved slot can never be unavailable. With no {@code item_model} set, it also renders
 * completely invisible while worn (true for every non-head slot - only the head slot falls back
 * to showing the item's own icon).
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
        ItemStack currentLeggings = inventory.getLeggings();
        if (currentLeggings != null && currentLeggings.getType() == Material.ARROW) {
            return; // already seeded
        }
        inventory.setLeggings(seedArrow());
    }

    private ItemStack seedArrow() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.LEGS);
        meta.setEquippable(equippable);
        arrow.setItemMeta(meta);
        return arrow;
    }
}
