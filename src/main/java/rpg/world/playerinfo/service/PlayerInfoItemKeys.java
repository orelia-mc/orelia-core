package rpg.world.playerinfo.service;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer key stamped onto the player-info Nether Star (see {@link PlayerInfoItemService}). */
public final class PlayerInfoItemKeys {

    private final NamespacedKey playerInfoItem;

    public PlayerInfoItemKeys(Plugin plugin) {
        this.playerInfoItem = new NamespacedKey(plugin, "player_info_item");
    }

    public NamespacedKey playerInfoItem() {
        return playerInfoItem;
    }
}
