package rpg.gathering.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatheringLevelingConfigTest {

    @Test
    void formulaModeComputesNextXpFromLevel() {
        GatheringLevelingConfig config = new GatheringLevelingConfig(
                GatheringLevelingConfig.Mode.FORMULA, 50, 1.5, 100, List.of(), 50);

        assertEquals(Math.round(50 * Math.pow(1, 1.5) + 100), config.requiredExperience(1));
        assertEquals(Math.round(50 * Math.pow(40, 1.5) + 100), config.requiredExperience(40));
    }

    @Test
    void tableModeUsesExplicitPerLevelValuesAndExtendsLastEntry() {
        GatheringLevelingConfig config = new GatheringLevelingConfig(
                GatheringLevelingConfig.Mode.TABLE, 0, 0, 0, List.of(100L, 200L, 300L), 50);

        assertEquals(100L, config.requiredExperience(1));
        assertEquals(200L, config.requiredExperience(2));
        assertEquals(300L, config.requiredExperience(3));
        // Level beyond the table falls back to the last configured entry.
        assertEquals(300L, config.requiredExperience(10));
    }

    @Test
    void emptyTableFallsBackToFormula() {
        GatheringLevelingConfig config = new GatheringLevelingConfig(
                GatheringLevelingConfig.Mode.TABLE, 10, 1, 5, List.of(), 50);

        assertEquals(15L, config.requiredExperience(1));
    }
}
