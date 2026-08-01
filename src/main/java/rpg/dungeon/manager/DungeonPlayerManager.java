package rpg.dungeon.manager;

import rpg.core.player.PlayerDataComponentLoader;
import rpg.dungeon.model.PlayerDungeonComponent;
import rpg.dungeon.repository.PlayerDungeonRepository;

import java.util.UUID;

/**
 * Bridges {@link PlayerDungeonRepository} to Core's player data lifecycle.
 */
public final class DungeonPlayerManager implements PlayerDataComponentLoader<PlayerDungeonComponent> {

    private final PlayerDungeonRepository repository;

    public DungeonPlayerManager(PlayerDungeonRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<PlayerDungeonComponent> type() {
        return PlayerDungeonComponent.class;
    }

    @Override
    public PlayerDungeonComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(PlayerDungeonComponent component) {
        repository.save(component);
    }
}
