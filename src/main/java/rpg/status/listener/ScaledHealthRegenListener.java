package rpg.status.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.attribute.Attribute;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;
import rpg.util.MathUtil;

/**
 * Vanilla healing (food-driven natural regen, golden apples, potions, ...) only ever changes
 * vanilla health directly - {@code EntityRegainHealthEvent}'s amount is already in vanilla
 * units and is left untouched here, so knockback-free natural healing keeps working exactly
 * as vanilla intends. This listener's only job is to mirror the same *percentage* gain into
 * the player's scaled {@code currentHp}, so the two stay in sync (the combat-damage path
 * mirrors the opposite direction - see {@code rpg.monster.listener.CombatDamageListener}).
 */
public final class ScaledHealthRegenListener implements Listener {

    private final StatusService statusService;

    public ScaledHealthRegenListener(StatusService statusService) {
        this.statusService = statusService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double vanillaMax = attribute != null ? attribute.getValue() : player.getHealth();
        if (vanillaMax <= 0) {
            return;
        }
        double scaledMax = statusService.getFinalStats(player.getUniqueId()).map(stats -> stats.get(StatType.HP)).orElse(0.0);
        if (scaledMax <= 0) {
            return;
        }
        double scaledHealAmount = (event.getAmount() / vanillaMax) * scaledMax;
        statusService.component(player.getUniqueId()).ifPresent(component ->
                component.setCurrentHp(MathUtil.clamp(component.getCurrentHp() + scaledHealAmount, 0, scaledMax)));
    }
}
