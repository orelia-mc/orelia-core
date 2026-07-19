package rpg.status.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import rpg.status.service.StatusService;

/**
 * Fall/fire/drowning/... damage fires a plain {@link EntityDamageEvent} (not
 * {@link EntityDamageByEntityEvent}), so {@code rpg.monster.listener.CombatDamageListener}
 * never sees it and never keeps the player's scaled {@code currentHp} in sync for it. Left
 * unhandled, vanilla health would drop from the environmental hit but {@code currentHp} would
 * stay unchanged, and the next sync (e.g. the next regen tick) would restore vanilla health
 * back up to the old (higher) percentage - looking like the player instantly healed back to
 * full right after taking fall damage. This mirrors the vanilla percentage lost onto
 * {@code currentHp} instead, the same idea as
 * {@code MonsterSpawnService#applyEnvironmentalDamage} on the monster side.
 */
public final class ScaledHealthEnvironmentalDamageListener implements Listener {

    private final StatusService statusService;

    public ScaledHealthEnvironmentalDamageListener(StatusService statusService) {
        this.statusService = statusService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return; // handled by CombatDamageListener instead
        }
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0) {
            return;
        }
        statusService.applyEnvironmentalDamage(player.getUniqueId(), event.getFinalDamage());
    }
}
