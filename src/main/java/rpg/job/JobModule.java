package rpg.job;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.DatabaseModule;
import rpg.job.command.JobCommand;
import rpg.job.config.JobConfigLoader;
import rpg.job.manager.JobManager;
import rpg.job.repository.PlayerJobRepository;
import rpg.job.service.JobService;
import rpg.status.StatusModule;

import java.util.logging.Level;

public final class JobModule implements RpgModule {

    private JobManager jobManager;
    private JobService jobService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "job";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseModule databaseModule = plugin.getModuleManager().get(DatabaseModule.class)
                .orElseThrow(() -> new IllegalStateException("job module requires database module"));
        StatusModule statusModule = plugin.getModuleManager().get(StatusModule.class)
                .orElseThrow(() -> new IllegalStateException("job module requires status module"));

        PlayerJobRepository repository = new PlayerJobRepository(databaseModule.getDatabaseManager());
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize job schema", e);
        }

        this.jobManager = new JobManager(repository);
        reloadDefinitions();
        plugin.getPlayerDataManager().registerLoader(jobManager);

        this.jobService = new JobService(plugin.getPlayerDataManager(), jobManager, statusModule.getStatusService());

        plugin.getPlayerCommandRegistry().register("job", new JobCommand(jobService, jobManager, plugin.getMessageManager()),
                "現在の職業を確認します。", "job [list]");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadDefinitions();
    }

    private void reloadDefinitions() {
        plugin.getConfigManager().register("jobs.yml");
        YamlConfiguration config = plugin.getConfigManager().get("jobs.yml").get();
        jobManager.setDefinitions(new JobConfigLoader().load(config));
    }

    public JobService getJobService() {
        return jobService;
    }

    public JobManager getJobManager() {
        return jobManager;
    }
}
