package rpg.gathering.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiningLuckConfigTest {

    @Test
    void defaultsToFifteenPercentWhenUnconfigured() {
        MiningLuckConfig config = new MiningLuckConfig();
        assertEquals(15.0, config.getBonusChancePercent());
    }

    @Test
    void usesValueGivenToConstructor() {
        MiningLuckConfig config = new MiningLuckConfig(30.0);
        assertEquals(30.0, config.getBonusChancePercent());
    }
}
