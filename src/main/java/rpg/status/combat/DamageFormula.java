package rpg.status.combat;

import rpg.util.MathUtil;

/**
 * The single source of truth for damage math shared across the weapon/skill/monster/status
 * combat listeners (SOW combat rules) - kept pure (no Bukkit dependency) so it's unit
 * testable in isolation.
 */
public final class DamageFormula {

    /**
     * Bukkit metadata key set on the attacking entity whenever {@link #rollCrit} lands, so a
     * later listener (e.g. the floating damage-number display) can tell a crit apart from a
     * normal hit without re-rolling. Callers must clear this when a hit is NOT a crit, so a
     * stale flag never leaks into the next attack.
     */
    public static final String CRIT_METADATA_KEY = "orelia_last_hit_crit";

    /**
     * Metadata key a skill sets on the caster while its own damage event is in flight, so
     * {@code rpg.monster.listener.CombatDamageListener} leaves that event's damage untouched
     * (the skill already ran the full {@link #compute} pipeline itself). See
     * {@code rpg.skill.executor.SkillDamage}.
     */
    public static final String SKILL_OVERRIDE_METADATA = "orelia_skill_active";

    /**
     * Metadata key set on the *victim* with the pre-vanilla-conversion "scaled" damage amount
     * (a player's/scaled monster's HP pool can be in the hundreds while their real vanilla
     * health stays near 20 - see {@code rpg.status.service.ScaledHealthService}), so the
     * floating damage-number display shows the meaningful RPG number instead of the tiny
     * vanilla-equivalent value actually written to {@code EntityDamageEvent#setDamage}.
     */
    public static final String SCALED_DAMAGE_METADATA_KEY = "orelia_scaled_damage_amount";

    /** Crit multiplier used when there's no weapon/monster to supply its own (bare hands). */
    public static final double DEFAULT_CRIT_MULTIPLIER = 1.5;

    /** Flat damage multiplier applied when a hit matches a monster's configured elemental weakness. */
    public static final double DEFAULT_WEAKNESS_MULTIPLIER = 1.5;

    private DamageFormula() {
    }

    /** {@code damage * (1 - defense/(defense+100))} - the shared defense-mitigation curve. */
    public static double mitigate(double damage, double defense) {
        return damage * (1 - defense / (defense + 100.0));
    }

    /** {@code damage * (1 + atkPercent/100)} - the shared attacker-stat bonus curve. */
    public static double applyAttackBonus(double damage, double atkPercent) {
        return damage * (1 + atkPercent / 100.0);
    }

    /**
     * The multiplier to apply on a crit: the weapon's/monster's own base crit multiplier
     * plus the attacker's {@code CRT_DMG} stat as an additive percentage bonus (e.g. a 1.5x
     * weapon with 20 CRT_DMG deals 1.5 + 20/100 = 1.7x).
     */
    public static double criticalMultiplier(double baseCritMultiplier, double critDmgPercent) {
        return baseCritMultiplier + critDmgPercent / 100.0;
    }

    /** Thin, semantically-named wrapper over {@link MathUtil#rollChance(double)} for crit rolls. */
    public static boolean rollCrit(double critRatePercent) {
        return MathUtil.rollChance(critRatePercent);
    }

    /** {@code weak ? damage * multiplier : damage} - flat bonus for hitting a monster's configured elemental weakness. */
    public static double applyElementalWeakness(double damage, boolean weak, double multiplier) {
        return weak ? damage * multiplier : damage;
    }

    public record DamageResult(double amount, boolean crit) {
    }

    /**
     * The single, decisive-order combat pipeline: base attack power -&gt; ATK% -&gt; DEF
     * mitigation -&gt; crit roll/multiplier -&gt; elemental weakness. Every damage-dealing hit
     * (weapon, bare hand, monster, skill) should compute through this one method rather than
     * applying these steps piecemeal across multiple listeners, so the order is never left to
     * Bukkit's undefined same-priority listener ordering.
     */
    public static DamageResult compute(double baseAttackPower, double atkPercent, double defense,
                                        double critRatePercent, double baseCritMultiplier, double critDmgPercent,
                                        boolean elementalWeak, double weaknessMultiplier) {
        double afterAtk = applyAttackBonus(baseAttackPower, atkPercent);
        double afterDef = mitigate(afterAtk, defense);
        boolean crit = rollCrit(critRatePercent);
        double afterCrit = crit ? afterDef * criticalMultiplier(baseCritMultiplier, critDmgPercent) : afterDef;
        double afterWeakness = applyElementalWeakness(afterCrit, elementalWeak, weaknessMultiplier);
        return new DamageResult(Math.max(0, afterWeakness), crit);
    }
}
