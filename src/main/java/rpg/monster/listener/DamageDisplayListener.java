package rpg.monster.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import rpg.monster.service.DamageDisplayService;
import rpg.status.combat.DamageFormula;

/**
 * Shows a floating damage number wherever a {@link LivingEntity} takes damage - works for
 * monsters and players alike. Runs at {@link EventPriority#MONITOR}, after damage is fully
 * resolved, and reads (then clears) {@link DamageFormula#CRIT_METADATA_KEY} off the damager
 * to color/scale crits differently from normal hits.
 *
 * <p>Ignores cancelled hits and zero/negative final damage (e.g. a weapon requirement wasn't
 * met, or defense fully absorbed the hit) - showing a number there would be misleading since no
 * damage actually landed. For a scaled victim (see {@code rpg.status.service.ScaledHealthService}),
 * prefers {@link DamageFormula#SCALED_DAMAGE_METADATA_KEY} over {@code event.getFinalDamage()} -
 * the latter is the tiny vanilla-equivalent amount actually applied to real health, not the
 * meaningful RPG-scale number a player expects to see.
 */
public final class DamageDisplayListener implements Listener {

    private final Plugin plugin;
    private final DamageDisplayService displayService;

    public DamageDisplayListener(Plugin plugin, DamageDisplayService displayService) {
        this.plugin = plugin;
        this.displayService = displayService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getFinalDamage() <= 0 || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        boolean isCrit = false;
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof LivingEntity attacker) {
            isCrit = attacker.hasMetadata(DamageFormula.CRIT_METADATA_KEY);
            attacker.removeMetadata(DamageFormula.CRIT_METADATA_KEY, plugin);
        }
        double amount = resolveDisplayAmount(victim, event.getFinalDamage());
        displayService.show(victim.getEyeLocation(), amount, isCrit);
    }

    private double resolveDisplayAmount(LivingEntity victim, double vanillaFinalDamage) {
        if (!victim.hasMetadata(DamageFormula.SCALED_DAMAGE_METADATA_KEY)) {
            return vanillaFinalDamage;
        }
        double scaledAmount = victim.getMetadata(DamageFormula.SCALED_DAMAGE_METADATA_KEY).get(0).asDouble();
        victim.removeMetadata(DamageFormula.SCALED_DAMAGE_METADATA_KEY, plugin);
        return scaledAmount;
    }
}
