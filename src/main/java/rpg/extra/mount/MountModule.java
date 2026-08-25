package rpg.extra.mount;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.api.CombatApi;
import rpg.api.StatusApi;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import rpg.extra.mount.command.MountCommand;
import rpg.extra.mount.config.MountGrowthLevelingConfig;
import rpg.extra.mount.listener.MountGrowthKillListener;
import rpg.extra.mount.listener.MountLifecycleListener;
import rpg.extra.mount.manager.MountGrowthManager;
import rpg.extra.mount.manager.MountManager;
import rpg.extra.mount.repository.MountConfigRepository;
import rpg.extra.mount.repository.MountGrowthRepository;
import rpg.extra.mount.repository.MountOwnershipRepository;
import rpg.extra.mount.service.MountGrowthService;
import rpg.extra.mount.service.MountService;

import java.util.logging.Level;

/**
 * Mount module: config-driven rideable mounts the player unlocks and summons/dismisses (SOW
 * MountModule). Money settles through Vault's {@link Economy}.
 */
public final class MountModule implements RpgModule {

    private final MountConfigRepository configRepository = new MountConfigRepository();
    private final MountManager mountManager = new MountManager();
    private final MountGrowthLevelingConfig growthLevelingConfig = new MountGrowthLevelingConfig();
    private MountService mountService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "mount";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("mount module requires OreliaCore's DatabaseManager");
        }
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);
        if (economy == null) {
            throw new IllegalStateException("mount module requires Vault's Economy service");
        }

        reloadMounts();
        growthLevelingConfig.load(plugin.getConfigManager().get("config.yml").get());

        // Cross-block dependency (Mount is social/economy, Status/Monster are foundation) -
        // legitimate here since ApiModule registers well before MountModule in the fixed order.
        // Same reasoning as PetModule's own StatusApi/CombatApi dependency, added for the same
        // growth feature.
        StatusApi statusApi = plugin.getServer().getServicesManager().load(StatusApi.class);
        if (statusApi == null) {
            throw new IllegalStateException("mount module requires OreliaCore's StatusApi");
        }
        CombatApi combatApi = plugin.getServer().getServicesManager().load(CombatApi.class);
        if (combatApi == null) {
            throw new IllegalStateException("mount module requires OreliaCore's CombatApi");
        }

        MountOwnershipRepository ownershipRepository = new MountOwnershipRepository(databaseManager);
        MountGrowthRepository growthRepository = new MountGrowthRepository(databaseManager);
        try {
            ownershipRepository.createSchemaIfNotExists();
            growthRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize mount schema", e);
        }
        plugin.getPlayerDataManager().registerLoader(new MountGrowthManager(growthRepository));

        MountGrowthService growthService = new MountGrowthService(plugin.getPlayerDataManager(), configRepository,
                statusApi, plugin.getMessageManager());
        mountManager.setOnDespawn(growthService::clearGrowthBonus);

        this.mountService = new MountService(configRepository, ownershipRepository, mountManager, economy, growthService);
        mountService.loadAll();

        plugin.getServer().getPluginManager().registerEvents(new MountLifecycleListener(mountManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new MountGrowthKillListener(combatApi, mountManager, mountService, growthService, growthLevelingConfig), plugin);
        MountCommand mountCommand = new MountCommand(mountService, plugin.getMessageManager());
        String description = "乗り物を管理します。";
        String usage = "mount [list|buy <id>|summon [id]|dismiss]";
        plugin.getPlayerCommandRegistry().register("mount", mountCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "mount", mountCommand, description, "[list|buy <id>|summon [id]|dismiss]");
    }

    @Override
    public void onDisable() {
        mountManager.despawnAll();
    }

    @Override
    public void onReload() {
        reloadMounts();
        growthLevelingConfig.load(plugin.getConfigManager().get("config.yml").get());
    }

    private void reloadMounts() {
        plugin.getConfigManager().register("mounts.yml");
        YamlConfiguration config = plugin.getConfigManager().get("mounts.yml").get();
        configRepository.load(config);
    }

    public MountService getMountService() {
        return mountService;
    }
}
