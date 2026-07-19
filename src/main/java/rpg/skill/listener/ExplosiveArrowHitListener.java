package rpg.skill.listener;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import rpg.skill.executor.ExplosiveArrowExecutor;
import rpg.status.combat.DamageFormula;

/**
 * Detonates arrows tagged by {@link ExplosiveArrowExecutor} (爆裂矢) on impact: entity
 * damage only in a radius around the hit point, no block damage, so the skill can't be
 * used to grief terrain.
 */
public final class ExplosiveArrowHitListener implements Listener {

    private final Plugin plugin;

    public ExplosiveArrowHitListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!arrow.hasMetadata(ExplosiveArrowExecutor.EXPLOSIVE_METADATA)) {
            return;
        }
        double[] payload = (double[]) arrow.getMetadata(ExplosiveArrowExecutor.EXPLOSIVE_METADATA).get(0).value();
        double amount = payload[0];
        double radius = payload[1];

        Location impact = arrow.getLocation();
        ProjectileSource shooter = arrow.getShooter();
        Player caster = shooter instanceof Player player ? player : null;

        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 1);
        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Without this metadata, CombatDamageListener would treat a Player-sourced hit as a
        // plain weapon swing and overwrite `amount` with the caster's held-weapon attack power
        // instead - same guard SkillDamage#apply uses.
        if (caster != null) {
            caster.setMetadata(DamageFormula.SKILL_OVERRIDE_METADATA, new FixedMetadataValue(plugin, true));
        }
        try {
            for (LivingEntity target : impact.getWorld().getNearbyLivingEntities(impact, radius, radius, radius)) {
                if (target.equals(caster)) {
                    continue;
                }
                if (caster != null) {
                    target.damage(amount, caster);
                } else {
                    target.damage(amount);
                }
            }
        } finally {
            if (caster != null) {
                caster.removeMetadata(DamageFormula.SKILL_OVERRIDE_METADATA, plugin);
            }
        }
        arrow.remove();
    }
}
