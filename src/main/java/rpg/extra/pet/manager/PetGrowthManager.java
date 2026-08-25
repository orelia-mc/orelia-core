package rpg.extra.pet.manager;

import rpg.core.player.PlayerDataComponentLoader;
import rpg.extra.pet.model.PetGrowthComponent;
import rpg.extra.pet.repository.PetGrowthRepository;

import java.util.UUID;

/** Bridges per-player pet growth level/experience to Core's player data lifecycle. */
public final class PetGrowthManager implements PlayerDataComponentLoader<PetGrowthComponent> {

    private final PetGrowthRepository repository;

    public PetGrowthManager(PetGrowthRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<PetGrowthComponent> type() {
        return PetGrowthComponent.class;
    }

    @Override
    public PetGrowthComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(PetGrowthComponent component) {
        repository.save(component);
    }
}
