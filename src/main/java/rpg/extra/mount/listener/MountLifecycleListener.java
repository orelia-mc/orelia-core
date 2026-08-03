package rpg.extra.mount.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.extra.mount.manager.MountManager;

/** Despawns a summoned mount when its rider dismounts or disconnects. */
public final class MountLifecycleListener implements Listener {

    private final MountManager mountManager;

    public MountLifecycleListener(MountManager mountManager) {
        this.mountManager = mountManager;
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && mountManager.isTrackedMount(event.getDismounted())) {
            mountManager.despawn(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mountManager.despawn(event.getPlayer().getUniqueId());
    }
}
