package rpg.status.service;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import rpg.util.MathUtil;

/**
 * Keeps an entity's real vanilla health (capped at a small, engine-safe range - 20 for a
 * player, a configurable cap for monsters, see {@code rpg.monster.service.MonsterSpawnService})
 * proportionally in sync with a separate "scaled" HP pool that can be arbitrarily large (a
 * player's {@code StatType.HP}, hundreds to thousands; a monster's {@code MonsterData.getHp()},
 * which can exceed Minecraft's practical attribute range for a fully-geared endgame boss).
 * Pure Bukkit-entity utility - callers supply the scaled current/max values from wherever they
 * actually live (a {@code PlayerStatusComponent}, a monster's PDC-backed counter, ...).
 */
public final class ScaledHealthService {

    private ScaledHealthService() {
    }

    /** Sets {@code entity}'s vanilla health to the same percentage as {@code scaledCurrent / scaledMax}. */
    public static void syncVanillaHealth(LivingEntity entity, double scaledCurrent, double scaledMax) {
        if (scaledMax <= 0) {
            return;
        }
        double vanillaMax = vanillaMaxHealth(entity);
        double target = MathUtil.clamp((scaledCurrent / scaledMax) * vanillaMax, 0, vanillaMax);
        entity.setHealth(target);
    }

    /**
     * Converts a scaled-space damage amount into the vanilla-health-equivalent amount to feed
     * into {@code EntityDamageEvent#setDamage} - lets Bukkit's own event resolution (knockback,
     * hurt sound/animation, death handling) apply to vanilla health naturally, instead of this
     * class calling {@code setHealth} itself. The caller is responsible for reducing the
     * tracked scaled current HP by {@code scaledDamage} separately.
     */
    public static double convertDamageToVanilla(LivingEntity victim, double scaledDamage, double scaledMax) {
        if (scaledMax <= 0) {
            return scaledDamage;
        }
        return (scaledDamage / scaledMax) * vanillaMaxHealth(victim);
    }

    private static double vanillaMaxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : entity.getHealth();
    }
}
