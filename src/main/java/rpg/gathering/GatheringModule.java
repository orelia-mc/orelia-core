package rpg.gathering;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.DatabaseModule;
import rpg.gathering.command.GatheringAdminCommand;
import rpg.gathering.command.GatheringCommand;
import rpg.gathering.config.FishingConfig;
import rpg.gathering.config.GatheringLevelingConfig;
import rpg.gathering.config.LevelRadiusConfig;
import rpg.gathering.config.MiningLuckConfig;
import rpg.gathering.listener.FarmingListener;
import rpg.gathering.listener.FishingListener;
import rpg.gathering.listener.GatherBlockBreakListener;
import rpg.gathering.listener.GatherBlockPlaceListener;
import rpg.gathering.listener.GatherChunkLoadListener;
import rpg.gathering.manager.GatheringManager;
import rpg.gathering.repository.BlockRegenRepository;
import rpg.gathering.repository.FishingLootRepository;
import rpg.gathering.repository.GatheringDefinitionRepository;
import rpg.gathering.repository.PlacedBlockRepository;
import rpg.gathering.repository.PlayerGatheringRepository;
import rpg.gathering.service.BlockRegenService;
import rpg.gathering.service.GatheringLevelService;
import rpg.gathering.service.PlacedBlockTrackingService;
import rpg.gathering.service.RegionProtectionService;
import rpg.job.JobModule;
import rpg.region.RegionModule;

import java.util.logging.Level;

/**
 * Gathering/farming module (SOW: 採取・農業拡張システム). Owns mining/woodcutting block
 * regeneration, bulk crop plant/harvest, and the level-based bulk-radius system (SOW
 * 3.1-3.3) - each of mining/woodcutting/farming levels its own {@link rpg.job.model.JobType}
 * (miner/woodcutter/farmer) independently, sharing only the same experience curve shape.
 */
public final class GatheringModule implements RpgModule {

    private GatheringDefinitionRepository definitions;
    private GatheringLevelingConfig levelingConfig;
    private LevelRadiusConfig radiusConfig;
    private MiningLuckConfig miningLuckConfig;
    private FishingConfig fishingConfig;
    private FishingLootRepository fishingLootRepository;
    private GatheringLevelService levelService;
    private BlockRegenService regenService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "gathering";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseModule databaseModule = plugin.getModuleManager().get(DatabaseModule.class)
                .orElseThrow(() -> new IllegalStateException("gathering module requires database module"));
        JobModule jobModule = plugin.getModuleManager().get(JobModule.class)
                .orElseThrow(() -> new IllegalStateException("gathering module requires job module"));
        RegionModule regionModule = plugin.getModuleManager().get(RegionModule.class)
                .orElseThrow(() -> new IllegalStateException("gathering module requires region module"));

        this.definitions = new GatheringDefinitionRepository(plugin.getLogger());
        this.levelingConfig = new GatheringLevelingConfig();
        this.radiusConfig = new LevelRadiusConfig();
        this.miningLuckConfig = new MiningLuckConfig();
        this.fishingConfig = new FishingConfig();
        this.fishingLootRepository = new FishingLootRepository(plugin.getLogger());
        reloadConfig();

        PlayerGatheringRepository playerRepository = new PlayerGatheringRepository(databaseModule.getDatabaseManager());
        BlockRegenRepository regenRepository = new BlockRegenRepository(databaseModule.getDatabaseManager());
        PlacedBlockRepository placedBlockRepository = new PlacedBlockRepository(databaseModule.getDatabaseManager());
        try {
            playerRepository.createSchemaIfNotExists();
            regenRepository.createSchemaIfNotExists();
            placedBlockRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize gathering schema", e);
        }

        GatheringManager gatheringManager = new GatheringManager(playerRepository);
        plugin.getPlayerDataManager().registerLoader(gatheringManager);

        this.levelService = new GatheringLevelService(plugin.getPlayerDataManager(), levelingConfig,
                jobModule.getJobManager());

        this.regenService = new BlockRegenService(plugin, plugin.getSchedulerService(), regenRepository);
        regenService.loadPending();
        YamlConfiguration config = plugin.getConfigManager().get("gathering.yml").get();
        regenService.start(config.getLong("regen-tick-period-ticks", 100L));

        RegionProtectionService protectionService = new RegionProtectionService(plugin);

        PlacedBlockTrackingService trackingService = new PlacedBlockTrackingService(plugin, plugin.getSchedulerService(),
                placedBlockRepository);
        trackingService.loadPlaced();

        plugin.getServer().getPluginManager().registerEvents(
                new GatherBlockBreakListener(definitions, regenService, levelService, protectionService,
                        jobModule.getJobManager(), trackingService, miningLuckConfig, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new GatherBlockPlaceListener(definitions, trackingService, regenService), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new FarmingListener(definitions, levelService, radiusConfig, protectionService), plugin);
        plugin.getServer().getPluginManager().registerEvents(new GatherChunkLoadListener(regenService), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new FishingListener(jobModule.getJobService(), plugin.getPlayerDataManager(), levelService,
                        fishingConfig, fishingLootRepository, regionModule.getQueryService(), plugin.getMessageManager()), plugin);

        plugin.getPlayerCommandRegistry().register("gathering",
                new GatheringCommand(levelService, radiusConfig, jobModule.getJobManager()),
                "採掘/伐採/農業のレベルを確認します。", "gathering");
        plugin.getAdminCommandRegistry().register("gathering",
                new GatheringAdminCommand(regenService, trackingService, plugin.getMessageManager()),
                "採取システムの再生成待ちタスク・手動設置追跡をリセットします。", "gathering resetregen confirm");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadConfig();
    }

    private void reloadConfig() {
        plugin.getConfigManager().register("gathering.yml");
        YamlConfiguration config = plugin.getConfigManager().get("gathering.yml").get();
        definitions.load(config);
        levelingConfig.load(config);
        radiusConfig.load(config);
        miningLuckConfig.load(config);

        plugin.getConfigManager().register("fishing.yml");
        YamlConfiguration fishingYml = plugin.getConfigManager().get("fishing.yml").get();
        fishingConfig.load(fishingYml);
        fishingLootRepository.load(fishingYml);
    }

    public GatheringLevelService getLevelService() {
        return levelService;
    }
}
