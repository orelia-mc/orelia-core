package rpg.status.listener;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;

/**
 * Folds ATK/BOW_ATK (attacker) and DEF (victim) into vanilla melee/projectile damage.
 * Melee hits land the player as the direct damager, so they use ATK; bow/crossbow hits
 * land the arrow as the damager, so they use BOW_ATK instead. Weapon skill damage
 * multipliers are applied separately by the skill module before this listener runs,
 * since skills fire a fresh damage event of their own.
 */
public final class CombatStatusListener implements Listener {

    private final StatusService statusService;

    public CombatStatusListener(StatusService statusService) {
        this.statusService = statusService;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        double damage = event.getDamage();

        Player attacker = resolveAttacker(event);
        if (attacker != null) {
            StatType attackStat = event.getDamager() instanceof Player ? StatType.ATK : StatType.BOW_ATK;
            StatSheet stats = statusService.getFinalStats(attacker.getUniqueId()).orElse(null);
            if (stats != null) {
                damage *= 1 + stats.get(attackStat) / 100.0;
            }
        }

        if (event.getEntity() instanceof Player victim) {
            StatSheet stats = statusService.getFinalStats(victim.getUniqueId()).orElse(null);
            if (stats != null) {
                double reduction = stats.get(StatType.DEF) / (stats.get(StatType.DEF) + 100.0);
                damage *= (1 - reduction);
            }
        }

        event.setDamage(Math.max(0, damage));
    }

    /** The player who dealt the damage, whether directly (melee) or via a shot arrow (bow/crossbow). */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
