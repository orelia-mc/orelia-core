package rpg.status.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import rpg.status.config.StatusGrowthConfig;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelGrowthServiceTest {

    private static final double DELTA = 1e-9;

    @Test
    void crtAndCritDmgStayAtBaseRegardlessOfLevelEvenWithAStrayPerLevelValue() {
        StatusGrowthConfig growthConfig = new StatusGrowthConfig();
        // A leftover per-level value (e.g. from a config.yml predating the fixed-stat change)
        // must have no effect - isFixed(CRT/CRT_DMG) callers ignore per-level entirely.
        growthConfig.load(YamlConfiguration.loadConfiguration(new StringReader("""
                status:
                  growth:
                    CRT:
                      base: 5
                      per-level: 1
                    CRT_DMG:
                      base: 7
                      per-level: 2
                """)));
        LevelGrowthService service = new LevelGrowthService(growthConfig);

        StatSheet level1 = service.baseStatsForLevel(1);
        StatSheet level80 = service.baseStatsForLevel(80);

        assertEquals(5.0, level1.get(StatType.CRT), DELTA);
        assertEquals(7.0, level1.get(StatType.CRT_DMG), DELTA);
        assertEquals(5.0, level80.get(StatType.CRT), DELTA);
        assertEquals(7.0, level80.get(StatType.CRT_DMG), DELTA);
    }

    @Test
    void otherLinearStatsStillGrowPerLevel() {
        StatusGrowthConfig growthConfig = new StatusGrowthConfig();
        growthConfig.load(YamlConfiguration.loadConfiguration(new StringReader("""
                status:
                  growth:
                    SPD:
                      base: 5
                      per-level: 1
                """)));
        LevelGrowthService service = new LevelGrowthService(growthConfig);

        assertEquals(5.0, service.baseStatsForLevel(1).get(StatType.SPD), DELTA);
        assertEquals(14.0, service.baseStatsForLevel(10).get(StatType.SPD), DELTA);
    }
}
