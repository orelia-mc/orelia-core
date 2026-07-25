package rpg.monster.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * PersistentDataContainer key {@link ProjectileAttackPowerListener} stamps onto a fired
 * arrow/thrown trident, read back by {@link CombatDamageListener} - shared between the two
 * since Bukkit has no attacker-stat concept for a projectile hit on its own.
 */
public final class ProjectileKeys {

    private final NamespacedKey attackPower;

    public ProjectileKeys(Plugin plugin) {
        this.attackPower = new NamespacedKey(plugin, "projectile_attack_power");
    }

    public NamespacedKey attackPower() {
        return attackPower;
    }
}
