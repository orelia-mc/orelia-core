package rpg.gui.framework;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.List;

/**
 * Builds a player-head icon for a roster/list GUI entry (guild members, party members, friends,
 * ...) - the one place this pattern lives instead of being copy-pasted per screen. An online
 * target gets a real {@code PLAYER_HEAD} with their actual skin; {@link SkullMeta#setOwningPlayer}
 * has no {@link ItemBuilder} hook (its generic {@code ItemMeta} wrapping doesn't know about
 * skull-specific meta), so this is built directly rather than through {@code ItemBuilder} - a
 * plain {@code new ItemBuilder(Material.PLAYER_HEAD)} always renders the default Steve skin
 * (see {@code StatusGuiScreen#headIcon}, the original instance of this pattern). An offline
 * target falls back to a skeleton skull instead of paying for a skin lookup that may block on
 * a Mojang API call.
 */
public final class GuiPlayerHead {

    private GuiPlayerHead() {
    }

    public static ItemStack build(OfflinePlayer target, String displayName, List<String> lore) {
        if (!target.isOnline()) {
            return new ItemBuilder(Material.SKELETON_SKULL).name(displayName).lore(lore).build();
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(ColorUtil.component(displayName));
        meta.lore(lore.stream().map(ColorUtil::component).toList());
        head.setItemMeta(meta);
        return head;
    }
}
