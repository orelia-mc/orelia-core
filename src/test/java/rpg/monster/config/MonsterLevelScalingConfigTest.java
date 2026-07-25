package rpg.monster.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonsterLevelScalingConfigTest {

    private static final double DELTA = 1e-9;

    private final MonsterLevelScalingConfig config = new MonsterLevelScalingConfig(1.1, 1.2, 1.05);

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
    void higherTargetLevelScalesEachStatExponentiallyByItsOwnGrowthFactor() {
        assertEquals(50.0 * Math.pow(1.1, 9), config.scaledHp(50.0, 10), DELTA);
        assertEquals(10.0 * Math.pow(1.2, 9), config.scaledAttackPower(10.0, 10), DELTA);
        assertEquals(5.0 * Math.pow(1.05, 9), config.scaledDefense(5.0, 10), DELTA);
    }

    @Test
    void lowTargetLevelScalesDownButNeverBelowFloor() {
        assertEquals(50.0 * Math.pow(1.1, -9), config.scaledHp(50.0, -8), DELTA);
        assertEquals(1.0, config.scaledHp(50.0, -1000), DELTA);
        assertEquals(0.0, config.scaledAttackPower(10.0, -1000), DELTA);
        assertEquals(0.0, config.scaledDefense(5.0, -1000), DELTA);
    }
}
