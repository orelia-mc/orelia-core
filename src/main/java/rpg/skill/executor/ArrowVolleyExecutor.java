package rpg.skill.executor;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import rpg.skill.model.SkillData;

/**
 * Fires one or more arrows dealing {@link SkillData#scaledDamageMultiplier} bonus damage
 * on top of vanilla bow damage. {@code radius == 0} fires a single, precise shot
 * (パワーショット); {@code radius > 0} fires three arrows spread across that many degrees
 * (マルチショット). Pickup is disabled and {@code rpg.skill.listener.ArrowSkillDamageListener}
 * removes each one a short time after it lands - without this, a player could farm free
 * infinite-ammo arrows by repeatedly casting the skill and walking over what it fired.
 */
public final class ArrowVolleyExecutor implements SkillExecutor {

    public static final String DAMAGE_MULTIPLIER_METADATA = "orelia_skill_arrow_multiplier";

    private final Plugin plugin;

    public ArrowVolleyExecutor(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player caster, SkillData data, int skillLevel) {
        double multiplier = data.scaledDamageMultiplier(skillLevel);
        int arrowCount = data.getRadius() > 0 ? 3 : 1;
        double spreadDegrees = data.getRadius();

        Vector base = caster.getLocation().getDirection().normalize();
        for (int i = 0; i < arrowCount; i++) {
            Vector direction = arrowCount == 1 ? base : rotateAroundY(base, spreadDegrees * (i - (arrowCount - 1) / 2.0) / arrowCount);
            Arrow arrow = caster.getWorld().spawnArrow(caster.getEyeLocation(), direction, 3.0f, 0f);
            arrow.setShooter(caster);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setMetadata(DAMAGE_MULTIPLIER_METADATA, new FixedMetadataValue(plugin, multiplier));
        }
    }

    private Vector rotateAroundY(Vector vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = vector.getX() * cos + vector.getZ() * sin;
        double z = -vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z).normalize();
    }
}
