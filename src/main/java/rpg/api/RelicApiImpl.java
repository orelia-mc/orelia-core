package rpg.api;

import org.bukkit.inventory.ItemStack;
import rpg.relic.service.RelicGenerationService;

import java.util.Optional;

final class RelicApiImpl implements RelicApi {

    private final RelicGenerationService generationService;

    RelicApiImpl(RelicGenerationService generationService) {
        this.generationService = generationService;
    }

    @Override
    public Optional<ItemStack> generateRelic(String sourceDungeonId) {
        return generationService.generate(sourceDungeonId);
    }
}
