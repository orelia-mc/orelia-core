package rpg.monster.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonsterLevelScalingConfigTest {

    private static final double DELTA = 1e-9;

    private final MonsterLevelScalingConfig config = new MonsterLevelScalingConfig(0.1, 0.2, 0.05);

    @Test
    void nullTargetLevelLeavesEveryStatUnchanged() {
        assertEquals(50.0, config.scaledHp(50.0, null), DELTA);
        assertEquals(10.0, config.scaledAttackPower(10.0, null), DELTA);
        assertEquals(5.0, config.scaledDefense(5.0, null), DELTA);
    }

    @Test
    void targetLevelOneLeavesEveryStatUnchanged() {
        assertEquals(50.0, config.scaledHp(50.0, 1), DELTA);
        assertEquals(10.0, config.scaledAttackPower(10.0, 1), DELTA);
        assertEquals(5.0, config.scaledDefense(5.0, 1), DELTA);
    }

    @Test
    void higherTargetLevelScalesEachStatByItsOwnFactor() {
        assertEquals(50.0 * 1.9, config.scaledHp(50.0, 10), DELTA);
        assertEquals(10.0 * 2.8, config.scaledAttackPower(10.0, 10), DELTA);
        assertEquals(5.0 * 1.45, config.scaledDefense(5.0, 10), DELTA);
    }

    @Test
    void lowTargetLevelScalesDownButNeverBelowFloor() {
        assertEquals(50.0 * 0.1, config.scaledHp(50.0, -8), DELTA);
        assertEquals(1.0, config.scaledHp(50.0, -100), DELTA);
        assertEquals(0.0, config.scaledAttackPower(10.0, -100), DELTA);
        assertEquals(0.0, config.scaledDefense(5.0, -100), DELTA);
    }
}
