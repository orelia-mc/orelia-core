package rpg.skill.executor;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import rpg.skill.model.SkillData;

import java.util.List;

/**
 * Damages every living entity within {@code radius} of the caster, regardless of facing.
 * Used for spin/slam-style skills such as 回転斬り, アースクラッシュ, バーサーク.
 */
public final class MeleeAoeExecutor implements SkillExecutor {

    private final SkillDamage skillDamage;

    public MeleeAoeExecutor(SkillDamage skillDamage) {
        this.skillDamage = skillDamage;
    }

    @Override
    public void execute(Player caster, SkillData data, int skillLevel) {
        double amount = skillDamage.baseDamage(caster, data, skillLevel);
        List<LivingEntity> targets = TargetFinder.inRadius(caster, data.getRadius());
        try {
            caster.getWorld().spawnParticle(Particle.valueOf(data.getEffectParticle()), TargetFinder.effectLocation(caster, targets), 40, data.getRadius() / 2, 0.5, data.getRadius() / 2);
        } catch (IllegalArgumentException ignored) {
        }
        for (LivingEntity target : targets) {
            skillDamage.apply(caster, target, amount);
            Vector knockback = target.getLocation().toVector()
                    .subtract(caster.getLocation().toVector())
                    .normalize()
                    .multiply(data.getKnockback());
            target.setVelocity(target.getVelocity().add(knockback));
        }
    }
}
