package rpg.town.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownConfigTest {

    @Test
    void defaultsToEnabledWithNoTownRegionsWhenUnconfigured() {
        TownConfig config = new TownConfig();
        config.load(YamlConfiguration.loadConfiguration(new StringReader("")));

        assertTrue(config.isEnabled());
        assertEquals(Set.of(), config.getTownRegions());
    }

    @Test
    void loadsTownRegionsAsLowercaseSet() {
        TownConfig config = new TownConfig();
        config.load(YamlConfiguration.loadConfiguration(new StringReader("""
                town-detection:
                  enabled: true
                  town-regions:
                    - Town1_AreaA
                    - town1_areab
                """)));

        assertTrue(config.isEnabled());
        assertEquals(Set.of("town1_areaa", "town1_areab"), config.getTownRegions());
    }

    @Test
    void respectsDisabledFlag() {
        TownConfig config = new TownConfig();
        config.load(YamlConfiguration.loadConfiguration(new StringReader("""
                town-detection:
                  enabled: false
                  town-regions:
                    - town1
                """)));

        assertFalse(config.isEnabled());
    }
}
