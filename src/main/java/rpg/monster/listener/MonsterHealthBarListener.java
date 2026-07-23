package rpg.monster.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterSpawnService;

/**
 * Keeps a tagged monster's nametag HP bar in sync after every hit - listens to the generic
 * {@link EntityDamageEvent} (not just by-entity) so fall/fire/other environmental damage
 * updates the bar too. Runs at {@link EventPriority#MONITOR}: purely a read-only display
 * update for {@code EntityDamageByEntityEvent} hits, since {@code CombatDamageListener}
 * (which runs earlier, at {@link EventPriority#LOW}) already updated the tracked scaled HP for
 * those. Environmental damage never goes through that listener, so this applies it here
 * instead, via {@link MonsterSpawnService#applyEnvironmentalDamage}.
 */
public final class MonsterHealthBarListener implements Listener {

    private final MonsterSpawnService spawnService;

    public MonsterHealthBarListener(MonsterSpawnService spawnService) {
        this.spawnService = spawnService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        MonsterData data = spawnService.dataOf(entity).orElse(null);
        if (data == null) {
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent) && event.getFinalDamage() > 0) {
            spawnService.applyEnvironmentalDamage(entity, data, event.getFinalDamage());
        }
        double scaledCurrent = spawnService.scaledCurrentHpOf(entity, data);
        spawnService.updateHealthBar(entity, data, scaledCurrent, spawnService.scaledMaxHpOf(entity, data));
    }
}
