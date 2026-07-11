package rpg.monster.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterSpawnService;

/**
 * Applies monster-side attack power (when the monster is the damager) and defense (when
 * the monster is the victim) on top of vanilla damage, mirroring how
 * {@link rpg.status.listener.CombatStatusListener} treats player ATK/DEF.
 */
public final class MonsterCombatListener implements Listener {

    private final MonsterSpawnService spawnService;

    public MonsterCombatListener(MonsterSpawnService spawnService) {
        this.spawnService = spawnService;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMonsterDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity attacker) {
            MonsterData data = spawnService.dataOf(attacker).orElse(null);
            if (data != null) {
                event.setDamage(data.getAttackPower());
            }
        }

        if (event.getEntity() instanceof LivingEntity victim && event.getDamager() instanceof Player) {
            MonsterData data = spawnService.dataOf(victim).orElse(null);
            if (data != null) {
                double reduction = data.getDefense() / (data.getDefense() + 100.0);
                event.setDamage(Math.max(0, event.getDamage() * (1 - reduction)));
            }
        }
    }
}
