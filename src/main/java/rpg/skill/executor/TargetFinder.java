package rpg.skill.executor;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nearby-entity queries shared by melee skill executors.
 */
final class TargetFinder {

    private TargetFinder() {
    }

    /**
     * {@code caster.getLocation()} is the caster's feet - fine for range/targeting math, but a
     * visual effect spawned there looks like it's coming out of the ground. This offsets up by
     * half the caster's hitbox height, so effect particles appear at their visual center instead.
     */
    static Location visualCenter(LivingEntity caster) {
        return caster.getLocation().add(0, caster.getHeight() / 2.0, 0);
    }

    /** Living entities within {@code range} blocks and roughly in front of the caster (within a ~70 degree cone). */
    static List<LivingEntity> inCone(Player caster, double range) {
        Vector facing = caster.getLocation().getDirection().normalize();
        return caster.getWorld().getNearbyLivingEntities(caster.getLocation(), range, range, range, entity ->
                entity != caster && isValidTarget(caster, entity) && isInFront(caster, facing, entity)).stream().collect(Collectors.toList());
    }

    /** Living entities within {@code radius} blocks of the caster, in any direction. */
    static List<LivingEntity> inRadius(Player caster, double radius) {
        return caster.getWorld().getNearbyLivingEntities(caster.getLocation(), radius, radius, radius,
                entity -> entity != caster && isValidTarget(caster, entity)).stream().collect(Collectors.toList());
    }

    /**
     * A melee skill's damage/knockback must not touch another player when the world has PvP
     * disabled - {@code SkillDamage#apply} delivers damage via a direct {@code Entity#damage}
     * call rather than the vanilla attack path, and knockback is an even more direct
     * {@code Entity#setVelocity}, so neither one goes through whatever normally enforces a
     * world's PvP flag on an ordinary player-vs-player hit. Monsters are never affected by this.
     */
    private static boolean isValidTarget(Player caster, LivingEntity entity) {
        return !(entity instanceof Player) || caster.getWorld().getPVP();
    }

    /**
     * Where a skill's effect (particle) should play: the nearest hit target's visual center if
     * {@code targets} isn't empty, otherwise a point 2 blocks in front of the caster (so a whiff
     * still shows the effect happening, rather than always centering it on the caster regardless
     * of where the skill actually landed).
     */
    static Location effectLocation(Player caster, List<LivingEntity> targets) {
        LivingEntity nearest = targets.stream()
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(caster.getLocation())))
                .orElse(null);
        if (nearest != null) {
            return visualCenter(nearest);
        }
        return visualCenter(caster).add(caster.getLocation().getDirection().normalize().multiply(2.0));
    }

    private static boolean isInFront(Player caster, Vector facing, LivingEntity entity) {
        Vector toEntity = entity.getLocation().toVector().subtract(caster.getLocation().toVector());
        if (toEntity.lengthSquared() == 0) {
            return true;
        }
        return facing.normalize().dot(toEntity.normalize()) > 0.5;
    }
}
