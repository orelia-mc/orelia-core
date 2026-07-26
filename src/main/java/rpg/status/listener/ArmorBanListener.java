package rpg.status.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import rpg.core.message.MessageManager;

/**
 * Vanilla armor is banned outright - defense comes entirely from the {@code DEF} stat, and
 * letting a player also stack vanilla armor points/toughness on top would double-dip (Bukkit
 * recomputes the {@code EntityDamageEvent} ARMOR modifier from whatever's actually equipped
 * *after* {@code CombatDamageListener} sets the DEF-mitigated base damage, so real armor would
 * silently reduce damage a second time on top of DEF). Rather than fighting every way an item
 * can land in an armor slot (click, shift-click, drag, dispenser, death/respawn with pre-existing
 * armor, ...), this listens for {@link PlayerArmorChangeEvent} - which Paper fires uniformly
 * after *any* of those - and reverts the slot whenever the new item is real armor, handing the
 * item back to the player's inventory (or dropping it if that's also full) rather than deleting
 * it. {@link #onJoin} does the same sweep for armor a player was already wearing before this
 * rule existed (e.g. an old character from before this update, or armor equipped by another
 * plugin) - {@code PlayerArmorChangeEvent} only fires on a *change*, so already-equipped armor
 * needs this separate one-time check.
 *
 * <p>Elytra and decorative head items (carved pumpkin, mob heads, ...) are deliberately left
 * alone - they occupy an armor slot too, but contribute no defense value, so banning them would
 * serve no purpose under this rule's own rationale. Only the four families of items with an
 * actual defense contribution ({@link #isRealArmor}) are banned.
 */
public final class ArmorBanListener implements Listener {

    private final MessageManager messages;

    public ArmorBanListener(MessageManager messages) {
        this.messages = messages;
    }

    /** Sweeps every already-online player once - called from {@code StatusModule#onEnable} so a plugin reload/restart also catches armor equipped before this rule existed. */
    public void banArmorForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            stripBannedArmor(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        stripBannedArmor(event.getPlayer());
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        ItemStack newItem = event.getNewItem();
        if (newItem == null || !isRealArmor(newItem.getType())) {
            return;
        }
        Player player = event.getPlayer();
        revertSlot(player, event.getSlot(), event.getOldItem());
        returnItem(player, newItem);
        messages.send(player, "item.armor-banned");
    }

    private void stripBannedArmor(Player player) {
        PlayerInventory inventory = player.getInventory();
        stripIfBanned(player, EquipmentSlot.HEAD, inventory.getHelmet());
        stripIfBanned(player, EquipmentSlot.CHEST, inventory.getChestplate());
        stripIfBanned(player, EquipmentSlot.LEGS, inventory.getLeggings());
        stripIfBanned(player, EquipmentSlot.FEET, inventory.getBoots());
    }

    private void stripIfBanned(Player player, EquipmentSlot slot, ItemStack piece) {
        if (piece == null || !isRealArmor(piece.getType())) {
            return;
        }
        revertSlot(player, slot, null);
        returnItem(player, piece);
    }

    private void revertSlot(Player player, EquipmentSlot slot, ItemStack restoreTo) {
        PlayerInventory inventory = player.getInventory();
        switch (slot) {
            case HEAD -> inventory.setHelmet(restoreTo);
            case CHEST -> inventory.setChestplate(restoreTo);
            case LEGS -> inventory.setLeggings(restoreTo);
            case FEET -> inventory.setBoots(restoreTo);
            default -> {
            }
        }
    }

    private void returnItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    /** Helmet/chestplate/leggings/boots across every material - the pieces that actually carry armor points/toughness. Elytra, pumpkins, and mob heads all fall outside this on purpose. */
    private boolean isRealArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }
}
