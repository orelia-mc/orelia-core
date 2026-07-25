package rpg.skill.executor;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import rpg.skill.model.SkillData;

import java.util.List;

/**
 * Damages every living entity in a cone in front of the caster. Used for line/slash-style
 * skills such as 斬撃 and クロススラッシュ.
 */
public final class MeleeConeExecutor implements SkillExecutor {

    private final SkillDamage skillDamage;

    public MeleeConeExecutor(SkillDamage skillDamage) {
        this.skillDamage = skillDamage;
    }

    @Override
    public void execute(Player caster, SkillData data, int skillLevel) {
        double amount = skillDamage.baseDamage(caster, data, skillLevel);
        List<LivingEntity> targets = TargetFinder.inCone(caster, data.getRange());
        spawnEffect(caster, data, targets);
        for (LivingEntity target : targets) {
            skillDamage.apply(caster, target, amount);
            Vector knockback = target.getLocation().toVector()
                    .subtract(caster.getLocation().toVector())
                    .normalize()
                    .multiply(data.getKnockback());
            target.setVelocity(target.getVelocity().add(knockback));
        }
    }

    private void spawnEffect(Player caster, SkillData data, List<LivingEntity> targets) {
        try {
            caster.getWorld().spawnParticle(Particle.valueOf(data.getEffectParticle()), TargetFinder.effectLocation(caster, targets), 20);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
