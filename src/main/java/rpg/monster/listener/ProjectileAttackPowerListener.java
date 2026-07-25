package rpg.monster.listener;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import rpg.item.model.WeaponData;
import rpg.item.service.WeaponIdentityService;

/**
 * Stamps the shooter's current bow/crossbow/trident base attack power onto a fired
 * arrow/thrown trident's PersistentDataContainer at launch, so {@link CombatDamageListener}
 * can apply it (plus the shooter's current ATK%/crit stats, read fresh at impact) on hit
 * instead of falling back to bare vanilla damage - {@code ProjectileLaunchEvent} fires for
 * both bow/crossbow-shot arrows and thrown tridents, covering both with one listener.
 */
public final class ProjectileAttackPowerListener implements Listener {

    private final WeaponIdentityService identityService;
    private final ProjectileKeys keys;

    public ProjectileAttackPowerListener(WeaponIdentityService identityService, ProjectileKeys keys) {
        this.identityService = identityService;
        this.keys = keys;
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        ItemStack weapon = shooter.getInventory().getItemInMainHand();
        WeaponData data = identityService.dataOf(weapon).orElse(null);
        if (data == null) {
            return;
        }
        double baseAttackPower = identityService.baseAttackPower(weapon, data);
        projectile.getPersistentDataContainer().set(keys.attackPower(), PersistentDataType.DOUBLE, baseAttackPower);
    }
}
