package rpg.gathering.config;

import org.junit.jupiter.api.Test;
import rpg.gathering.model.LevelRange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelRadiusConfigTest {

    private final LevelRadiusConfig config = new LevelRadiusConfig(List.of(
            new LevelRange(1, 9, 0),
            new LevelRange(10, 19, 1),
            new LevelRange(20, 29, 2),
            new LevelRange(30, 39, 3),
            new LevelRange(40, 50, 4)));

    @Test
    void mapsLevelWithinEachBandToItsRadius() {
        assertEquals(0, config.radiusForLevel(1));
        assertEquals(0, config.radiusForLevel(9));
        assertEquals(1, config.radiusForLevel(10));
        assertEquals(2, config.radiusForLevel(25));
        assertEquals(3, config.radiusForLevel(39));
        assertEquals(4, config.radiusForLevel(50));
    }

    @Test
    void levelAboveHighestBandKeepsTheHighestBandRadius() {
        assertEquals(4, config.radiusForLevel(100));
    }

    @Test
    void emptyRangesMeansSingleBlockOnly() {
        LevelRadiusConfig empty = new LevelRadiusConfig(List.of());
        assertEquals(0, empty.radiusForLevel(50));
    }
}
