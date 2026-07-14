package rpg.monster.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import rpg.item.model.ElementType;
import rpg.item.model.WeaponData;
import rpg.item.service.WeaponIdentityService;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterSpawnService;

/**
 * Applies monster-side attack power (when the monster is the damager) and defense (when
 * the monster is the victim) on top of vanilla damage, mirroring how
 * {@link rpg.status.listener.CombatStatusListener} treats player ATK/DEF. Also applies a
 * flat weakness multiplier when the attacker's equipped weapon element matches the
 * monster's configured weak point.
 */
public final class MonsterCombatListener implements Listener {

    private static final double WEAKNESS_MULTIPLIER = 1.5;

    private final MonsterSpawnService spawnService;
    private final WeaponIdentityService weaponIdentityService;

    public MonsterCombatListener(MonsterSpawnService spawnService, WeaponIdentityService weaponIdentityService) {
        this.spawnService = spawnService;
        this.weaponIdentityService = weaponIdentityService;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMonsterDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity attacker) {
            MonsterData data = spawnService.dataOf(attacker).orElse(null);
            if (data != null) {
                event.setDamage(data.getAttackPower());
            }
        }

        if (event.getEntity() instanceof LivingEntity victim && event.getDamager() instanceof Player attacker) {
            MonsterData data = spawnService.dataOf(victim).orElse(null);
            if (data != null) {
                double reduction = data.getDefense() / (data.getDefense() + 100.0);
                double damage = event.getDamage() * (1 - reduction);
                if (isWeaknessHit(attacker, data)) {
                    damage *= WEAKNESS_MULTIPLIER;
                }
                event.setDamage(Math.max(0, damage));
            }
        }
    }

    private boolean isWeaknessHit(Player attacker, MonsterData data) {
        if (data.getWeakness() == ElementType.NONE) {
            return false;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        return weaponIdentityService.dataOf(weapon)
                .map(WeaponData::getElement)
                .map(element -> element == data.getWeakness())
                .orElse(false);
    }
}
