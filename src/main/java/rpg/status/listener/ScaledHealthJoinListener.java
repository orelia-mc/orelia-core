package rpg.status.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import rpg.status.model.StatType;
import rpg.status.service.ScaledHealthService;
import rpg.status.service.StatusService;

/**
 * Handles the two lifecycle moments where vanilla health and scaled {@code currentHp} can fall
 * out of sync in ways no other listener catches:
 *
 * <ul>
 *   <li>Join - a player's persisted scaled HP percentage was never reflected in their vanilla
 *       health while offline (nothing changes vanilla health for an offline player), so this
 *       re-syncs it the moment they connect.</li>
 *   <li>Respawn - Bukkit resets vanilla health to full on respawn, but nothing resets the
 *       *scaled* {@code currentHp}, which stays at (or near) 0 from the killing blow. Without
 *       this, the next regen tick or hit would read that stale ~0 currentHp and drag the
 *       freshly-respawned player's vanilla health back down to ~0 too.</li>
 * </ul>
 */
public final class ScaledHealthJoinListener implements Listener {

    private final StatusService statusService;

    public ScaledHealthJoinListener(StatusService statusService) {
        this.statusService = statusService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sync(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        statusService.component(player.getUniqueId()).ifPresent(component -> {
            double max = statusService.getFinalStats(player.getUniqueId()).map(stats -> stats.get(StatType.HP)).orElse(0.0);
            component.setCurrentHp(max);
        });
        // Bukkit will set vanilla health to full for the respawn itself right after this event
        // returns, so no explicit syncVanillaHealth call is needed here - just fixing the
        // stored scaled value is enough to keep future syncs correct.
    }

    private void sync(Player player) {
        statusService.component(player.getUniqueId()).ifPresent(component -> {
            double max = statusService.getFinalStats(player.getUniqueId()).map(stats -> stats.get(StatType.HP)).orElse(0.0);
            ScaledHealthService.syncVanillaHealth(player, component.getCurrentHp(), max);
        });
    }
}
