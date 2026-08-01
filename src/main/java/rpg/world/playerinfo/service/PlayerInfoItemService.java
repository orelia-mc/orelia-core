package rpg.world.playerinfo.service;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import rpg.util.ItemBuilder;

import java.util.List;

/**
 * Creates and identifies the "プレイヤー情報" Nether Star: a menu item every player always
 * carries in the rightmost hotbar slot to open {@link rpg.world.playerinfo.gui.PlayerInfoGuiScreen}.
 */
public final class PlayerInfoItemService {

    /** Rightmost hotbar slot (hotbar is slots 0-8 in {@link org.bukkit.inventory.PlayerInventory}). */
    public static final int HOTBAR_SLOT = 8;

    private final PlayerInfoItemKeys keys;

    public PlayerInfoItemService(PlayerInfoItemKeys keys) {
        this.keys = keys;
    }

    public ItemStack createItem() {
        return new ItemBuilder(Material.NETHER_STAR)
                .name("&%d&lプレイヤー情報")
                .lore(List.of("&%7右クリックでクエスト・ジョブ・", "&%7スキル・実績を確認できます。"))
                .tag(keys.playerInfoItem(), PersistentDataType.BYTE, (byte) 1)
                .build();
    }

    public boolean isPlayerInfoItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(keys.playerInfoItem(), PersistentDataType.BYTE);
    }

    /** Places a fresh copy in the player's rightmost hotbar slot if it isn't already there. */
    public void ensureInHotbar(Player player) {
        ItemStack current = player.getInventory().getItem(HOTBAR_SLOT);
        if (isPlayerInfoItem(current)) {
            return;
        }
        if (current != null && !current.getType().isAir()) {
            player.getInventory().addItem(current).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.getInventory().setItem(HOTBAR_SLOT, createItem());
    }
}
