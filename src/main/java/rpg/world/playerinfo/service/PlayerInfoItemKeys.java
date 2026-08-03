package rpg.world.playerinfo.service;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/** PersistentDataContainer key stamped onto the player-info Nether Star (see {@link PlayerInfoItemService}). */
public final class PlayerInfoItemKeys {

    /**
     * The namespace this key carried before the orelia-core/orelia-world/orelia-extra merge,
     * when the Nether Star was issued by the separate OreliaWorld plugin.
     *
     * <p>A {@link NamespacedKey}'s namespace comes from the owning plugin's name, so the merge
     * moved this key from {@code oreliaworld:player_info_item} to
     * {@code oreliacore:player_info_item} - every Nether Star already sitting in a player's
     * inventory still carries the old one. Without reading it, that star stops being recognized:
     * it no longer opens the menu, loses its drop/move protection, and
     * {@link PlayerInfoItemService#ensureInHotbar} pushes it out of the hotbar slot on the next
     * join (into the inventory, or onto the ground if the inventory is full) to make room for a
     * freshly issued one - leaving the player with a useless duplicate.
     *
     * <p>Read-only fallback: unlike an entity's container, an {@code ItemStack}'s is reached
     * through a copied {@code ItemMeta}, so re-stamping would need every caller to write the meta
     * back. Reading both keys costs nothing and newly created stars already carry the current one.
     *
     * <p>Removable once no pre-merge Nether Star is in circulation.
     */
    private static final NamespacedKey LEGACY_PLAYER_INFO_ITEM =
            Objects.requireNonNull(NamespacedKey.fromString("oreliaworld:player_info_item"));

    private final NamespacedKey playerInfoItem;

    public PlayerInfoItemKeys(Plugin plugin) {
        this.playerInfoItem = new NamespacedKey(plugin, "player_info_item");
    }

    public NamespacedKey playerInfoItem() {
        return playerInfoItem;
    }

    /** @see #LEGACY_PLAYER_INFO_ITEM */
    public NamespacedKey legacyPlayerInfoItem() {
        return LEGACY_PLAYER_INFO_ITEM;
    }
}
