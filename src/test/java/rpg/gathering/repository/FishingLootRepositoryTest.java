package rpg.gathering.repository;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import rpg.gathering.model.FishingLootEntry;

import java.io.StringReader;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingLootRepositoryTest {

    private static final Logger LOGGER = Logger.getLogger(FishingLootRepositoryTest.class.getName());

    private final FishingLootRepository repository = load("""
            towns:
              default:
                - item: COD
                  weight: 1
                  min-amount: 1
                  max-amount: 1
              world_name:
                - item: SALMON
                  weight: 1
                  min-amount: 1
                  max-amount: 1
              town1_areaa:
                - item: PRISMARINE_SHARD
                  weight: 1
                  min-amount: 1
                  max-amount: 1
            """);

    @Test
    void picksMostSpecificMatchingCandidateKeyFirst() {
        List<FishingLootEntry> loot = repository.lootFor(List.of("town1_areaa", "world_name"));

        assertEquals(1, loot.size());
        assertEquals(Material.PRISMARINE_SHARD, loot.get(0).item());
    }

    @Test
    void fallsBackToLessSpecificKeyWhenFirstCandidateHasNoBucket() {
        List<FishingLootEntry> loot = repository.lootFor(List.of("unregistered_region", "world_name"));

        assertEquals(1, loot.size());
        assertEquals(Material.SALMON, loot.get(0).item());
    }

    @Test
    void fallsBackToDefaultWhenNoCandidateKeyMatches() {
        List<FishingLootEntry> loot = repository.lootFor(List.of("unregistered_region_a", "unregistered_region_b"));

        assertEquals(1, loot.size());
        assertEquals(Material.COD, loot.get(0).item());
    }

    @Test
    void singleKeyOverloadBehavesLikeASingleCandidateList() {
        List<FishingLootEntry> loot = repository.lootFor("town1_areaa");

        assertTrue(loot.stream().anyMatch(entry -> entry.item() == Material.PRISMARINE_SHARD));
    }

    private static FishingLootRepository load(String yaml) {
        FishingLootRepository repository = new FishingLootRepository(LOGGER);
        repository.load(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
        return repository;
    }
}
