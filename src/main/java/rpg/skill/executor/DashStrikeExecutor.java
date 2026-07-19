package rpg.skill.executor;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import rpg.skill.model.SkillData;

import java.util.Comparator;
import java.util.List;

/**
 * Propels the caster forward and strikes the nearest entity in front of them. Used for
 * dash-in skills such as 居合, 突進, スパイラルランス, ジャンプ突き, バーサーク.
 */
public final class DashStrikeExecutor implements SkillExecutor {

    private final SkillDamage skillDamage;

    public DashStrikeExecutor(SkillDamage skillDamage) {
        this.skillDamage = skillDamage;
    }

    @Override
    public void execute(Player caster, SkillData data, int skillLevel) {
        Vector dash = caster.getLocation().getDirection().normalize().multiply(data.getRange() / 4.0).setY(0.2);
        caster.setVelocity(dash);

        try {
            caster.getWorld().spawnParticle(Particle.valueOf(data.getEffectParticle()), TargetFinder.visualCenter(caster), 15);
        } catch (IllegalArgumentException ignored) {
        }

        List<LivingEntity> candidates = TargetFinder.inCone(caster, data.getRange());
        LivingEntity nearest = candidates.stream()
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(caster.getLocation())))
                .orElse(null);
        if (nearest == null) {
            return;
        }
        double amount = skillDamage.baseDamage(caster, data, skillLevel);
        skillDamage.apply(caster, nearest, amount);
        nearest.setVelocity(nearest.getVelocity().add(dash.clone().multiply(data.getKnockback())));
    }
}
