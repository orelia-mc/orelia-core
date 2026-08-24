package rpg.world.playerinfo.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import rpg.gui.framework.GuiManager;
import rpg.world.playerinfo.gui.PlayerInfoGuiScreen;
import rpg.world.playerinfo.service.PlayerInfoItemService;

/**
 * Keeps the "プレイヤー情報" Nether Star in the player's rightmost hotbar slot, blocks it
 * from being thrown with Q, opens {@link PlayerInfoGuiScreen} on right click, and pins it
 * in place so it can't be moved, swapped out, or trashed while the player's own inventory
 * screen is open.
 *
 * <p>Creative-mode players manipulate their own inventory screen through a separate
 * {@link InventoryCreativeEvent} rather than {@link InventoryClickEvent} - shift-clicking
 * the item away or dropping it on the delete/trash slot both fire that event instead, so it
 * needs its own handler or the pinning above is silently bypassed in creative mode.
 */
public final class PlayerInfoItemListener implements Listener {

    private final PlayerInfoItemService itemService;
    private final PlayerInfoGuiScreen guiScreen;
    private final GuiManager guiManager;

    public PlayerInfoItemListener(PlayerInfoItemService itemService, PlayerInfoGuiScreen guiScreen, GuiManager guiManager) {
        this.itemService = itemService;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        itemService.ensureInHotbar(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        itemService.ensureInHotbar(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (itemService.isPlayerInfoItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!itemService.isPlayerInfoItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        guiManager.open(player, guiScreen.build(player));
    }

    /**
     * Pins the item to its hotbar slot: cancels any click that targets that slot directly,
     * that holds the item on the clicked slot or the cursor (covers shift-click and
     * click-outside-to-trash), or that hotbar-swaps another item into the slot.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER
                && event.getSlot() == PlayerInfoItemService.HOTBAR_SLOT) {
            event.setCancelled(true);
            return;
        }
        if (itemService.isPlayerInfoItem(event.getCurrentItem()) || itemService.isPlayerInfoItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == PlayerInfoItemService.HOTBAR_SLOT) {
            event.setCancelled(true);
        }
    }

    /** Blocks dragging the item itself across slots (started by picking it up, already covered above). */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (itemService.isPlayerInfoItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    /**
     * Creative-mode equivalent of {@link #onInventoryClick}: shift-clicking the item away
     * (from any creative tab) or dropping it on the trash slot both arrive here instead of
     * as an {@link InventoryClickEvent}, so the pinning logic has to be duplicated for it.
     */
    @EventHandler
    public void onInventoryCreativeClick(InventoryCreativeEvent event) {
        if (event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER
                && event.getSlot() == PlayerInfoItemService.HOTBAR_SLOT) {
            event.setCancelled(true);
            return;
        }
        if (itemService.isPlayerInfoItem(event.getCurrentItem()) || itemService.isPlayerInfoItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }
}
