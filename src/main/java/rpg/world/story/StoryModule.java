package rpg.world.story;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.world.story.manager.StoryManager;
import rpg.world.story.repository.PlayerStoryRepository;
import rpg.world.story.repository.StoryRepository;
import rpg.world.story.service.StoryProgressService;

import java.util.logging.Level;

/**
 * Story module: chapter/scenario/flag/ending progression (SOW StoryModule), loaded from
 * {@code story.yml}.
 */
public final class StoryModule implements RpgModule {

    private final StoryRepository repository = new StoryRepository();
    private StoryProgressService progressService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "story";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("story module requires OreliaCore's DatabaseManager");
        }

        reloadStory();

        PlayerStoryRepository playerRepository = new PlayerStoryRepository(databaseManager);
        try {
            playerRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize story schema", e);
        }
        plugin.getPlayerDataManager().registerLoader(new StoryManager(playerRepository));

        this.progressService = new StoryProgressService(repository, plugin.getPlayerDataManager());
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadStory();
    }

    private void reloadStory() {
        plugin.getConfigManager().register("story.yml");
        YamlConfiguration config = plugin.getConfigManager().get("story.yml").get();
        repository.load(config);
    }

    public StoryProgressService getProgressService() {
        return progressService;
    }

    public StoryRepository getRepository() {
        return repository;
    }
}
