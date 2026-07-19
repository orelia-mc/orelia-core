package rpg.skill.executor;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import rpg.item.model.WeaponData;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.model.SkillData;
import rpg.status.combat.DamageFormula;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;

/**
 * Shared helpers every {@link SkillExecutor} archetype uses: computing a skill's base attack
 * power (weapon attack power x enhancement x the skill's own damage multiplier, then the
 * caster's ATK%) once per cast - not per target, since AOE/cone skills apply the same amount
 * to every entity they hit - and delivering it through the normal Bukkit damage event while
 * telling {@code rpg.monster.listener.CombatDamageListener} that DEF/crit/elemental weakness
 * still need to run against each individual target (everything after ATK% in
 * {@link DamageFormula#compute} is inherently per-target and can't be precomputed here).
 */
public final class SkillDamage {

    private final Plugin plugin;
    private final WeaponIdentityService identityService;
    private final StatusService statusService;

    public SkillDamage(Plugin plugin, WeaponIdentityService identityService, StatusService statusService) {
        this.plugin = plugin;
        this.identityService = identityService;
        this.statusService = statusService;
    }

    /** Base attack power after ATK%, before DEF/crit/elemental weakness (applied per-target by {@code CombatDamageListener}). */
    public double baseDamage(Player caster, SkillData data, int skillLevel) {
        var weapon = caster.getInventory().getItemInMainHand();
        WeaponData weaponData = identityService.dataOf(weapon).orElse(null);
        double baseAttackPower = weaponData != null
                ? weaponData.getAttackPower() * identityService.enhancementMultiplier(weapon)
                : 1.0;
        baseAttackPower *= data.scaledDamageMultiplier(skillLevel);

        StatSheet stats = statusService.getFinalStats(caster.getUniqueId()).orElse(null);
        double atkPercent = stats != null ? stats.get(StatType.ATK) : 0;
        return DamageFormula.applyAttackBonus(baseAttackPower, atkPercent);
    }

    public void apply(Player caster, LivingEntity target, double amount) {
        caster.setMetadata(DamageFormula.SKILL_OVERRIDE_METADATA, new FixedMetadataValue(plugin, true));
        try {
            target.damage(amount, caster);
        } finally {
            caster.removeMetadata(DamageFormula.SKILL_OVERRIDE_METADATA, plugin);
        }
    }
}
