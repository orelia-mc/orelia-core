package rpg.world.story.manager;

import rpg.core.player.PlayerDataComponentLoader;
import rpg.world.story.model.PlayerStoryComponent;
import rpg.world.story.repository.PlayerStoryRepository;

import java.util.UUID;

public final class StoryManager implements PlayerDataComponentLoader<PlayerStoryComponent> {

    private final PlayerStoryRepository repository;

    public StoryManager(PlayerStoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<PlayerStoryComponent> type() {
        return PlayerStoryComponent.class;
    }

    @Override
    public PlayerStoryComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(PlayerStoryComponent component) {
        repository.save(component);
    }
}
