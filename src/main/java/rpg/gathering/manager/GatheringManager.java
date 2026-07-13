package rpg.gathering.manager;

import rpg.core.player.PlayerDataComponentLoader;
import rpg.gathering.model.PlayerGatheringComponent;
import rpg.gathering.repository.PlayerGatheringRepository;

import java.util.UUID;

/** Bridges per-player gathering level/experience to Core's player data lifecycle. */
public final class GatheringManager implements PlayerDataComponentLoader<PlayerGatheringComponent> {

    private final PlayerGatheringRepository repository;

    public GatheringManager(PlayerGatheringRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<PlayerGatheringComponent> type() {
        return PlayerGatheringComponent.class;
    }

    @Override
    public PlayerGatheringComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(PlayerGatheringComponent component) {
        repository.save(component);
    }
}
