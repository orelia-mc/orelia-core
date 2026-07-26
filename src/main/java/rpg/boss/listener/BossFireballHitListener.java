package rpg.boss.listener;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import rpg.boss.service.BossAbilityCastService;
import rpg.status.combat.DamageFormula;

/**
 * Detonates fireballs fired by {@link BossAbilityCastService#castFireballBarrage}: entity
 * damage only in a radius around the impact point, no block damage - same "don't grief
 * terrain" rule as {@code rpg.skill.listener.ExplosiveArrowHitListener}.
 */
public final class BossFireballHitListener implements Listener {

    private final Plugin plugin;

    public BossFireballHitListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity().hasMetadata(BossAbilityCastService.FIREBALL_METADATA)) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof SmallFireball fireball) || !fireball.hasMetadata(BossAbilityCastService.FIREBALL_METADATA)) {
            return;
        }
        double[] payload = (double[]) fireball.getMetadata(BossAbilityCastService.FIREBALL_METADATA).get(0).value();
        double damage = payload[0];
        double radius = payload[1];

        Location impact = fireball.getLocation();
        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 1);
        // Both monster (MonsterAbilityCastService) and boss (BossAbilityCastService) fireball
        // barrages share this one listener - the shooter is whichever LivingEntity cast it.
        LivingEntity shooter = fireball.getShooter() instanceof LivingEntity living ? living : null;
        for (Player player : impact.getWorld().getNearbyPlayers(impact, radius)) {
            if (shooter != null) {
                player.setMetadata(DamageFormula.LAST_ABILITY_ATTACKER_METADATA_KEY, new FixedMetadataValue(plugin, shooter));
            }
            try {
                player.damage(damage);
            } finally {
                if (shooter != null) {
                    player.removeMetadata(DamageFormula.LAST_ABILITY_ATTACKER_METADATA_KEY, plugin);
                }
            }
        }
        fireball.remove();
    }
}
