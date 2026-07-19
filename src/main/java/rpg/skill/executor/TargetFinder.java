package rpg.skill.executor;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

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
                entity != caster && isInFront(caster, facing, entity)).stream().collect(Collectors.toList());
    }

    /** Living entities within {@code radius} blocks of the caster, in any direction. */
    static List<LivingEntity> inRadius(Player caster, double radius) {
        return caster.getWorld().getNearbyLivingEntities(caster.getLocation(), radius, radius, radius,
                entity -> entity != caster).stream().collect(Collectors.toList());
    }

    private static boolean isInFront(Player caster, Vector facing, LivingEntity entity) {
        Vector toEntity = entity.getLocation().toVector().subtract(caster.getLocation().toVector());
        if (toEntity.lengthSquared() == 0) {
            return true;
        }
        return facing.normalize().dot(toEntity.normalize()) > 0.5;
    }
}
