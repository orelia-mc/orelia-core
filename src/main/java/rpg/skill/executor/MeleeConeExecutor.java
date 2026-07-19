package rpg.skill.executor;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import rpg.skill.model.SkillData;

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
        spawnEffect(caster, data);
        for (LivingEntity target : TargetFinder.inCone(caster, data.getRange())) {
            skillDamage.apply(caster, target, amount);
            Vector knockback = target.getLocation().toVector()
                    .subtract(caster.getLocation().toVector())
                    .normalize()
                    .multiply(data.getKnockback());
            target.setVelocity(target.getVelocity().add(knockback));
        }
    }

    private void spawnEffect(Player caster, SkillData data) {
        try {
            caster.getWorld().spawnParticle(Particle.valueOf(data.getEffectParticle()), TargetFinder.visualCenter(caster), 20);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
