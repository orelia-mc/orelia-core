package rpg.item.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.util.ColorUtil;

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
 * reserved slot can never be unavailable.
 *
 * <p>{@link EquippableComponent#setModel} (the "asset_id") only controls how the item renders
 * *worn on the body* - left unset, that's invisible in every non-head slot, which is what we
 * want. It does nothing for how the item looks *as an icon* (inventory screen, held in hand,
 * dropped on the ground), which still shows a plain arrow unless the item's own
 * {@code item_model} is overridden - {@link #seedArrow} sets that to a barrier block and names
 * it "No Slot" so a player who opens their inventory sees something that visibly isn't a normal
 * item, rather than a mysteriously immovable arrow. {@link #onClick}/{@link #onDrag} additionally
 * block moving it out of the legs slot via the inventory screen (this is a swap-in-place lock,
 * not a real removal - reaching for it does nothing, rather than duplicating it into the
 * player's cursor while a replacement re-seeds into the now-current-if-different legs slot).
 */
public final class BowAmmoListener implements Listener {

    private static final NamespacedKey SEEDED_MODEL_KEY = NamespacedKey.minecraft("barrier");

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
        if (isFullySeeded(inventory.getLeggings())) {
            return; // already seeded with the current visual/lock
        }
        inventory.setLeggings(seedArrow());
    }

    /** Locks the seeded arrow in place - clicking it (armor-slot icon or a shift-click targeting it) does nothing, rather than moving it to the cursor. */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && isSeedArrow(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    /** Same lock for the drag-to-fill-multiple-slots gesture, in case it ever targets the legs slot. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (event.getView().getSlotType(rawSlot) == InventoryType.SlotType.ARMOR
                    && isSeedArrow(event.getView().getItem(rawSlot))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isSeedArrow(ItemStack stack) {
        return stack != null && stack.getType() == Material.ARROW;
    }

    /**
     * True only for an arrow already carrying this listener's current visual/lock (item model
     * + display name). A plain {@code Material.ARROW} with no such meta - e.g. one seeded by an
     * older build of this listener, before the barrier-block visual existed - fails this check
     * so {@link #onInteract} reseeds it instead of leaving the stale, unmodified-looking arrow
     * in place forever.
     */
    private boolean isFullySeeded(ItemStack stack) {
        if (!isSeedArrow(stack)) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasItemModel() && SEEDED_MODEL_KEY.equals(meta.getItemModel());
    }

    private ItemStack seedArrow() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.LEGS);
        equippable.setSwappable(false);
        meta.setEquippable(equippable);
        meta.setItemModel(NamespacedKey.minecraft("barrier"));
        meta.displayName(ColorUtil.component("&%cNo Slot"));
        arrow.setItemMeta(meta);
        return arrow;
    }
}
