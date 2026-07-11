package rpg.status;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.DatabaseModule;
import rpg.status.config.LevelingConfig;
import rpg.status.config.StatusGrowthConfig;
import rpg.status.listener.CombatStatusListener;
import rpg.status.manager.StatusManager;
import rpg.status.repository.StatusRepository;
import rpg.status.service.LevelGrowthService;
import rpg.status.service.StatusCalculatorService;
import rpg.status.service.StatusService;

import java.util.logging.Level;

/**
 * Owns character level/stat calculation and HP/SP regen. Every other module reads a
 * player's effective stats through {@link #getStatusService()} rather than computing
 * their own copy.
 */
public final class StatusModule implements RpgModule {

    private StatusGrowthConfig growthConfig;
    private LevelingConfig levelingConfig;
    private StatusService statusService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseModule databaseModule = plugin.getModuleManager().get(DatabaseModule.class)
                .orElseThrow(() -> new IllegalStateException("status module requires database module"));

        this.growthConfig = new StatusGrowthConfig();
        this.levelingConfig = new LevelingConfig();
        loadGrowthConfig(plugin);

        LevelGrowthService levelGrowthService = new LevelGrowthService(growthConfig);
        StatusRepository repository = new StatusRepository(databaseModule.getDatabaseManager(), levelGrowthService);
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize status schema", e);
        }

        StatusManager statusManager = new StatusManager(repository);
        plugin.getPlayerDataManager().registerLoader(statusManager);

        StatusCalculatorService calculatorService = new StatusCalculatorService();
        this.statusService = new StatusService(plugin.getPlayerDataManager(), calculatorService, levelGrowthService, levelingConfig, repository);

        plugin.getServer().getPluginManager().registerEvents(new CombatStatusListener(statusService), plugin);

        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        double hpRegen = config.getDouble("status.regen.hp-percent-per-tick", 0.5);
        double spRegen = config.getDouble("status.regen.sp-percent-per-tick", 1.0);
        long periodTicks = config.getLong("status.regen.period-ticks", 100L);
        plugin.getSchedulerService().runTimer(() ->
                plugin.getServer().getOnlinePlayers().forEach(player ->
                        statusService.tickRegen(player.getUniqueId(), hpRegen, spRegen)),
                periodTicks, periodTicks);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        loadGrowthConfig(plugin);
    }

    private void loadGrowthConfig(OreliaPlugin plugin) {
        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        growthConfig.load(config);
        levelingConfig.load(config);
    }

    public StatusService getStatusService() {
        return statusService;
    }
}
