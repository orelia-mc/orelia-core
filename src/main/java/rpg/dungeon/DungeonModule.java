package rpg.dungeon;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.api.CombatApi;
import rpg.api.RelicApi;
import rpg.api.StatusApi;
import rpg.core.command.CommandAliasUtil;
import rpg.database.manager.DatabaseManager;
import rpg.dungeon.command.DungeonAdminCommand;
import rpg.dungeon.command.DungeonCommand;
import rpg.dungeon.gui.DungeonGuiScreen;
import rpg.dungeon.listener.DungeonBlockInteractListener;
import rpg.dungeon.listener.DungeonMobDeathListener;
import rpg.dungeon.listener.DungeonQuitListener;
import rpg.dungeon.manager.DungeonInstanceManager;
import rpg.dungeon.manager.DungeonPlayerManager;
import rpg.dungeon.repository.DungeonBlockRepository;
import rpg.dungeon.repository.DungeonRepository;
import rpg.dungeon.repository.PlayerDungeonRepository;
import rpg.dungeon.service.DungeonEncounterService;
import rpg.dungeon.service.DungeonService;
import rpg.extra.api.PartyApi;
import rpg.gui.framework.GuiManager;
import rpg.quest.QuestModule;
import rpg.quest.service.QuestProgressService;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;

import java.util.logging.Level;

/**
 * Dungeon module: config-driven dungeon definitions (dungeons.yml), the trigger-block
 * unlock/challenge flow, the live encounter (enemy/boss spawn, kill tracking, time limit),
 * and completion rewards. Registered after {@link QuestModule} so a cleared dungeon can
 * feed {@link QuestProgressService#onDungeonCleared} directly.
 */
public final class DungeonModule implements RpgModule {

    private final DungeonRepository repository = new DungeonRepository();
    private final DungeonInstanceManager instanceManager = new DungeonInstanceManager();
    private DungeonBlockRepository blockRepository;
    private DungeonService dungeonService;
    private DungeonEncounterService encounterService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "dungeon";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        StatusApi statusApi = plugin.getServer().getServicesManager().load(StatusApi.class);
        if (statusApi == null) {
            throw new IllegalStateException("dungeon module requires OreliaCore's StatusApi");
        }
        CombatApi combatApi = plugin.getServer().getServicesManager().load(CombatApi.class);
        if (combatApi == null) {
            throw new IllegalStateException("dungeon module requires OreliaCore's CombatApi");
        }
        RelicApi relicApi = plugin.getServer().getServicesManager().load(RelicApi.class);
        if (relicApi == null) {
            throw new IllegalStateException("dungeon module requires OreliaCore's RelicApi");
        }
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("dungeon module requires OreliaCore's DatabaseManager");
        }
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);
        // Soft dependency - only used to resolve a dungeon challenger's real party; null when
        // OreliaExtra isn't installed, in which case every challenge falls back to solo.
        PartyApi partyApi = plugin.getServer().getServicesManager().load(PartyApi.class);
        QuestProgressService questProgressService = plugin.getModuleManager().get(QuestModule.class)
                .orElseThrow(() -> new IllegalStateException("dungeon module requires quest module"))
                .getProgressService();

        reloadDungeons();

        PlayerDungeonRepository playerDungeonRepository = new PlayerDungeonRepository(databaseManager);
        this.blockRepository = new DungeonBlockRepository(databaseManager);
        try {
            playerDungeonRepository.createSchemaIfNotExists();
            blockRepository.createSchemaIfNotExists();
            blockRepository.loadAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize dungeon schema", e);
        }
        plugin.getPlayerDataManager().registerLoader(new DungeonPlayerManager(playerDungeonRepository));

        this.dungeonService = new DungeonService(repository, instanceManager, statusApi, economy);
        this.encounterService = new DungeonEncounterService(dungeonService, instanceManager, combatApi, relicApi, statusApi,
                plugin.getSchedulerService(), plugin.getConfigManager(), playerDungeonRepository,
                plugin.getPlayerDataManager(), partyApi, questProgressService, plugin.getMessageManager());

        GuiManager guiManager = new GuiManager();
        DungeonGuiScreen guiScreen = new DungeonGuiScreen(repository, encounterService, plugin.getPlayerDataManager(),
                statusApi, guiManager, plugin.getMessageManager());

        plugin.getServer().getPluginManager().registerEvents(new DungeonQuitListener(instanceManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new DungeonMobDeathListener(encounterService), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new DungeonBlockInteractListener(blockRepository, guiScreen, guiManager, plugin.getPlayerDataManager(), plugin.getMessageManager()), plugin);

        DungeonCommand dungeonCommand = new DungeonCommand(encounterService, guiScreen, guiManager,
                plugin.getPlayerDataManager(), plugin.getMessageManager());
        String dungeonDescription = "ダンジョンの一覧・挑戦・離脱を行います。";
        plugin.getPlayerCommandRegistry().register("dungeon", dungeonCommand, dungeonDescription, "dungeon [list]|start <id>|retire");
        CommandAliasUtil.registerAlias(plugin, "dungeon", dungeonCommand, dungeonDescription, "[list]|start <id>|retire");

        plugin.getAdminCommandRegistry().register("dungeonblock",
                new DungeonAdminCommand(blockRepository, repository, plugin.getMessageManager()),
                "ダンジョンの開放トリガーブロックを設置・解除します。", "dungeonblock set <dungeon-id>|remove|list [page]");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadDungeons();
    }

    private void reloadDungeons() {
        plugin.getConfigManager().register("dungeons.yml");
        YamlConfiguration config = plugin.getConfigManager().get("dungeons.yml").get();
        repository.load(config);
    }

    public DungeonService getDungeonService() {
        return dungeonService;
    }

    public DungeonEncounterService getEncounterService() {
        return encounterService;
    }

    public DungeonRepository getRepository() {
        return repository;
    }

    public DungeonInstanceManager getInstanceManager() {
        return instanceManager;
    }
}
