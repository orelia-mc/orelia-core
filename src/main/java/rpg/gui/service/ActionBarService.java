package rpg.gui.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.item.model.WeaponData;
import rpg.item.service.WeaponIdentityService;
import rpg.status.combat.DamageFormula;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;
import rpg.util.ColorUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the periodic HP/SP/current-attack-power action bar HUD. "Current attack power" is
 * the same base-attack-power-after-ATK% value {@code rpg.monster.listener.CombatDamageListener}
 * feeds into {@link DamageFormula#compute} - what the player would actually swing for before
 * the opponent's own DEF/crit/weakness are factored in.
 *
 * <p>Also carries short-lived skill-activation feedback ({@link #showTransient}) appended next
 * to the HP/SP/ATK line, so {@code rpg.skill.listener.SkillActivationListener} doesn't have to
 * spam chat on every cast (success, on-cooldown, etc. - see the relic system's Part Q follow-up).
 */
public final class ActionBarService {

    private final StatusService statusService;
    private final WeaponIdentityService weaponIdentityService;
    private final Map<UUID, TransientStatus> transientStatuses = new ConcurrentHashMap<>();
    private String format = "";
    private boolean enabled = true;

    private record TransientStatus(String text, long expiresAtMillis) {
    }

    public ActionBarService(StatusService statusService, WeaponIdentityService weaponIdentityService) {
        this.statusService = statusService;
        this.weaponIdentityService = weaponIdentityService;
    }

    public void setFormat(String format) {
        this.format = format == null ? "" : format;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Shows {@code text} appended to the next {@link #send} calls for {@code durationMillis}, then reverts to plain HP/SP/ATK. */
    public void showTransient(Player player, String text, long durationMillis) {
        transientStatuses.put(player.getUniqueId(), new TransientStatus(text, System.currentTimeMillis() + durationMillis));
    }

    /** Called every {@code config.yml: action-bar.period-ticks} for each online player. */
    public void send(Player player) {
        if (!enabled || format.isBlank()) {
            return;
        }
        PlayerStatusComponent component = statusService.component(player.getUniqueId()).orElse(null);
        if (component == null) {
            return;
        }
        StatSheet stats = statusService.getFinalStats(player.getUniqueId()).orElse(null);
        double maxHp = stats != null ? stats.get(StatType.HP) : 0;
        double maxSp = stats != null ? stats.get(StatType.SP) : 0;
        String message = format
                .replace("{hp}", format(component.getCurrentHp()))
                .replace("{max_hp}", format(maxHp))
                .replace("{sp}", format(component.getCurrentSp()))
                .replace("{max_sp}", format(maxSp))
                .replace("{atk}", format(currentAttackPower(player, stats)));
        String skillStatus = currentTransientText(player.getUniqueId());
        if (!skillStatus.isEmpty()) {
            message = message + " &%7| " + skillStatus;
        }
        player.sendActionBar(ColorUtil.component(message));
    }

    private String currentTransientText(UUID uuid) {
        TransientStatus status = transientStatuses.get(uuid);
        if (status == null) {
            return "";
        }
        if (System.currentTimeMillis() > status.expiresAtMillis()) {
            transientStatuses.remove(uuid);
            return "";
        }
        return status.text();
    }

    private double currentAttackPower(Player player, StatSheet stats) {
        double atkPercent = stats != null ? stats.get(StatType.ATK) : 0;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        WeaponData data = weaponIdentityService.dataOf(weapon).orElse(null);
        if (data == null) {
            // Bare hand: the ATK stat IS the base attack power directly, same special case
            // CombatDamageListener applies (no separate ATK% layer on top of itself).
            return atkPercent;
        }
        double baseAttackPower = weaponIdentityService.baseAttackPower(weapon, data);
        return DamageFormula.applyAttackBonus(baseAttackPower, atkPercent);
    }

    private String format(double value) {
        return String.valueOf(Math.round(value));
    }
}
