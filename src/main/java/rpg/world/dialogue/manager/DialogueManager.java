package rpg.world.dialogue.manager;

import rpg.core.player.PlayerDataComponentLoader;
import rpg.world.dialogue.model.PlayerDialogueComponent;
import rpg.world.dialogue.repository.PlayerDialogueRepository;

import java.util.UUID;

public final class DialogueManager implements PlayerDataComponentLoader<PlayerDialogueComponent> {

    private final PlayerDialogueRepository repository;

    public DialogueManager(PlayerDialogueRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<PlayerDialogueComponent> type() {
        return PlayerDialogueComponent.class;
    }

    @Override
    public PlayerDialogueComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(PlayerDialogueComponent component) {
        repository.save(component);
    }
}
