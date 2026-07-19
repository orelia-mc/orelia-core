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

/**
 * Renders the periodic HP/SP/current-attack-power action bar HUD. "Current attack power" is
 * the same base-attack-power-after-ATK% value {@code rpg.monster.listener.CombatDamageListener}
 * feeds into {@link DamageFormula#compute} - what the player would actually swing for before
 * the opponent's own DEF/crit/weakness are factored in.
 */
public final class ActionBarService {

    private final StatusService statusService;
    private final WeaponIdentityService weaponIdentityService;
    private String format = "";
    private boolean enabled = true;

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
        player.sendActionBar(ColorUtil.component(message));
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
