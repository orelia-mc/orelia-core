package rpg.status.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageFormulaTest {

    private static final double DELTA = 1e-9;

    @Test
    void mitigateHalvesDamageAtEqualDefense() {
        // defense == damage-ish reference point: def/(def+100) reduction curve
        assertEquals(50.0, DamageFormula.mitigate(100.0, 100.0), DELTA);
    }

    @Test
    void mitigateWithZeroDefenseAppliesNoReduction() {
        assertEquals(100.0, DamageFormula.mitigate(100.0, 0.0), DELTA);
    }

    @Test
    void applyAttackBonusScalesDamageByAtkPercent() {
        assertEquals(150.0, DamageFormula.applyAttackBonus(100.0, 50.0), DELTA);
    }

    @Test
    void applyAttackBonusWithZeroAtkIsNoOp() {
        assertEquals(100.0, DamageFormula.applyAttackBonus(100.0, 0.0), DELTA);
    }

    @Test
    void criticalMultiplierAddsCritDmgAsPercentBonus() {
        assertEquals(1.7, DamageFormula.criticalMultiplier(1.5, 20.0), DELTA);
    }

    @Test
    void criticalMultiplierWithZeroCritDmgReturnsBaseMultiplier() {
        assertEquals(1.5, DamageFormula.criticalMultiplier(1.5, 0.0), DELTA);
    }

    @Test
    void applyElementalWeaknessMultipliesOnlyWhenWeak() {
        assertEquals(150.0, DamageFormula.applyElementalWeakness(100.0, true, 1.5), DELTA);
        assertEquals(100.0, DamageFormula.applyElementalWeakness(100.0, false, 1.5), DELTA);
    }

    @Test
    void applyElementalDamageBonusScalesDamageByBonusPercent() {
        assertEquals(120.0, DamageFormula.applyElementalDamageBonus(100.0, 20.0), DELTA);
        assertEquals(100.0, DamageFormula.applyElementalDamageBonus(100.0, 0.0), DELTA);
    }

    @Test
    void computeAppliesStepsInOrderAtkThenDefThenWeakness_noCrit() {
        // critRatePercent 0 guarantees no crit, isolating the other three steps.
        DamageFormula.DamageResult result = DamageFormula.compute(
                4.0, 10.0, 0.0, 0.0, 1.5, 20.0, false, 1.5, 0.0);
        // 4.0 * 1.10 (ATK%) = 4.4; mitigate by defense 0 is a no-op; no crit; not weak; no elemental bonus.
        assertEquals(4.4, result.amount(), DELTA);
        assertFalse(result.crit());
    }

    @Test
    void computeAppliesCritAfterDefAndWeaknessAfterCritThenElementalBonus() {
        // critRatePercent 100 guarantees a crit every time.
        DamageFormula.DamageResult result = DamageFormula.compute(
                4.0, 10.0, 0.0, 100.0, 1.5, 20.0, true, 1.5, 10.0);
        // 4.0 * 1.10 = 4.4 (ATK%) -> 4.4 (DEF no-op) -> * 1.7 (crit) = 7.48 -> * 1.5 (weakness) = 11.22 -> * 1.10 (elemental) = 12.342
        assertEquals(12.342, result.amount(), DELTA);
        assertTrue(result.crit());
    }

    @Test
    void computeClampsNegativeDamageToZero() {
        // A large enough negative ATK% debuff can drive the running total negative;
        // compute() must clamp the final result rather than returning it as-is.
        DamageFormula.DamageResult result = DamageFormula.compute(10.0, -200.0, 0.0, 0.0, 1.5, 0.0, false, 1.5, 0.0);
        assertEquals(0.0, result.amount(), DELTA);
    }
}
