package rpg.skill.listener;

import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import rpg.core.scheduler.SchedulerService;
import rpg.skill.executor.ArrowVolleyExecutor;

/**
 * Applies the bow skill damage multiplier {@link ArrowVolleyExecutor} stamps onto its
 * arrows, on top of vanilla bow damage (which already reflects draw strength). Also removes
 * one of those arrows a short time after it lands ({@code config.yml}'s
 * {@code skill.arrow-despawn-ticks}) - combined with {@link ArrowVolleyExecutor} already
 * disabling pickup on them, this closes off farming free arrows by repeatedly casting the
 * skill and collecting what it fired.
 */
public final class ArrowSkillDamageListener implements Listener {

    private final SchedulerService schedulerService;
    private final long despawnTicks;

    public ArrowSkillDamageListener(SchedulerService schedulerService, long despawnTicks) {
        this.schedulerService = schedulerService;
        this.despawnTicks = despawnTicks;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) {
            return;
        }
        if (!arrow.hasMetadata(ArrowVolleyExecutor.DAMAGE_MULTIPLIER_METADATA)) {
            return;
        }
        double multiplier = arrow.getMetadata(ArrowVolleyExecutor.DAMAGE_MULTIPLIER_METADATA).get(0).asDouble();
        event.setDamage(event.getDamage() * multiplier);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !arrow.hasMetadata(ArrowVolleyExecutor.DAMAGE_MULTIPLIER_METADATA)) {
            return;
        }
        schedulerService.runLater(() -> {
            if (arrow.isValid()) {
                arrow.remove();
            }
        }, despawnTicks);
    }
}
