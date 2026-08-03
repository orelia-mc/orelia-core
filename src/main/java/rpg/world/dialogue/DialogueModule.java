package rpg.world.dialogue;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.world.dialogue.command.DialogueChoiceCommand;
import rpg.world.dialogue.manager.DialogueManager;
import rpg.world.dialogue.repository.DialogueRepository;
import rpg.world.dialogue.repository.PlayerDialogueRepository;
import rpg.world.dialogue.service.DialogueSessionService;

import java.util.logging.Level;

/**
 * Dialogue module: conversation trees (dialogues.yml) with branching entry points,
 * player choices, and flag updates (SOW DialogueModule).
 */
public final class DialogueModule implements RpgModule {

    private final DialogueRepository repository = new DialogueRepository();
    private DialogueSessionService sessionService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "dialogue";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("dialogue module requires OreliaCore's DatabaseManager");
        }

        reloadDialogues();

        PlayerDialogueRepository playerRepository = new PlayerDialogueRepository(databaseManager);
        try {
            playerRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize dialogue schema", e);
        }
        plugin.getPlayerDataManager().registerLoader(new DialogueManager(playerRepository));

        this.sessionService = new DialogueSessionService(repository, plugin.getPlayerDataManager());
        plugin.getPlayerCommandRegistry().register("dialoguechoice", new DialogueChoiceCommand(sessionService),
                "対話の選択肢を選びます（内部用）。", "dialoguechoice <index>");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadDialogues();
    }

    private void reloadDialogues() {
        plugin.getConfigManager().register("dialogues.yml");
        YamlConfiguration config = plugin.getConfigManager().get("dialogues.yml").get();
        repository.load(config);
    }

    public DialogueSessionService getSessionService() {
        return sessionService;
    }

    public DialogueRepository getRepository() {
        return repository;
    }
}
