package rpg.quest.manager;

import rpg.core.player.PlayerDataComponentLoader;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.repository.PlayerQuestRepository;

import java.util.UUID;

/**
 * Bridges {@link PlayerQuestRepository} to Core's player data lifecycle.
 */
public final class QuestManager implements PlayerDataComponentLoader<PlayerQuestComponent> {

    private final PlayerQuestRepository repository;

    public QuestManager(PlayerQuestRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<PlayerQuestComponent> type() {
        return PlayerQuestComponent.class;
    }

    @Override
    public PlayerQuestComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(PlayerQuestComponent component) {
        repository.save(component);
    }
}
