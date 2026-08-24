package rpg.dungeon.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.dungeon.manager.DungeonInstanceManager;

/**
 * Frees a disconnecting player's dungeon-membership mapping so they are not permanently
 * treated as "already in a dungeon" if they log back in later. The instance itself (and
 * any other party members still inside) is left untouched.
 */
public final class DungeonQuitListener implements Listener {

    private final DungeonInstanceManager instanceManager;

    public DungeonQuitListener(DungeonInstanceManager instanceManager) {
        this.instanceManager = instanceManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        instanceManager.removePlayer(event.getPlayer().getUniqueId());
    }
}
