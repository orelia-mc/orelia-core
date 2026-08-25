package rpg.extra.mount.manager;

import rpg.core.player.PlayerDataComponentLoader;
import rpg.extra.mount.model.MountGrowthComponent;
import rpg.extra.mount.repository.MountGrowthRepository;

import java.util.UUID;

/** Bridges per-player mount growth level/experience to Core's player data lifecycle. */
public final class MountGrowthManager implements PlayerDataComponentLoader<MountGrowthComponent> {

    private final MountGrowthRepository repository;

    public MountGrowthManager(MountGrowthRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<MountGrowthComponent> type() {
        return MountGrowthComponent.class;
    }

    @Override
    public MountGrowthComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(MountGrowthComponent component) {
        repository.save(component);
    }
}
