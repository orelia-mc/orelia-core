package rpg.gathering.service;

import org.junit.jupiter.api.Test;
import rpg.gathering.config.LevelRadiusConfig;
import rpg.gathering.model.LevelRange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BulkRadiusResolverTest {

    private final LevelRadiusConfig radiusConfig = new LevelRadiusConfig(List.of(
            new LevelRange(1, 9, 0),
            new LevelRange(10, 19, 1),
            new LevelRange(20, 29, 2),
            new LevelRange(30, 39, 3),
            new LevelRange(40, 50, 4)));

    @Test
    void equippedToolRadiusWithNoToolIsZero() {
        assertEquals(0, BulkRadiusResolver.equippedToolRadius(50, 0, 0));
    }

    @Test
    void equippedToolRadiusWithToolRadiusZeroIsZero() {
        assertEquals(0, BulkRadiusResolver.equippedToolRadius(50, 0, 5));
    }

    @Test
    void equippedToolRadiusBelowToolRequiredLevelIsZero() {
        assertEquals(0, BulkRadiusResolver.equippedToolRadius(10, 3, 20));
    }

    @Test
    void equippedToolRadiusAtOrAboveToolRequiredLevelUsesToolRadius() {
        assertEquals(3, BulkRadiusResolver.equippedToolRadius(20, 3, 20));
        assertEquals(3, BulkRadiusResolver.equippedToolRadius(30, 3, 20));
    }

    @Test
    void levelBasedRadiusWhileSneakingDelegatesToLevelRadiusConfig() {
        assertEquals(2, BulkRadiusResolver.levelBasedRadius(true, 25, radiusConfig));
    }

    @Test
    void levelBasedRadiusWhileNotSneakingIsZeroRegardlessOfLevel() {
        assertEquals(0, BulkRadiusResolver.levelBasedRadius(false, 50, radiusConfig));
    }
}
