package rpg.npc;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicesManager;
import rpg.api.GuiApi;
import rpg.api.ItemApi;
import rpg.api.RelicApi;
import rpg.gui.framework.GuiManager;
import rpg.npc.command.NpcAdminCommand;
import rpg.npc.command.NpcSpawnCommand;
import rpg.npc.listener.NpcInteractListener;
import rpg.npc.repository.NpcRepository;
import rpg.npc.service.NpcAdminService;
import rpg.npc.service.NpcKeys;
import rpg.npc.service.NpcSpawnService;
import rpg.npc.service.NpcSpawnSyncService;
import rpg.quest.QuestModule;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;

/**
 * NPC module: config-driven NPC placement (npc.yml) and dispatching interactions to
 * orelia-core's shop/job-change/warehouse/enhancement/relic-upgrade screens (via
 * {@link GuiApi}/{@link ItemApi}/{@link RelicApi}) or orelia-world's own quest screen.
 */
public final class NpcModule implements RpgModule {

    private final NpcRepository repository = new NpcRepository();
    private NpcSpawnService spawnService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "npc";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        ServicesManager services = plugin.getServer().getServicesManager();

        GuiApi guiApi = services.load(GuiApi.class);
        ItemApi itemApi = services.load(ItemApi.class);
        RelicApi relicApi = services.load(RelicApi.class);
        if (guiApi == null || itemApi == null || relicApi == null) {
            throw new IllegalStateException("npc module requires OreliaCore's GuiApi, ItemApi and RelicApi");
        }
        Economy economy = services.load(Economy.class);

        QuestModule questModule = plugin.getModuleManager().get(QuestModule.class)
                .orElseThrow(() -> new IllegalStateException("npc module requires quest module"));

        reloadNpcs();

        NpcKeys keys = new NpcKeys(plugin);
        this.spawnService = new NpcSpawnService(keys, repository);
        NpcSpawnSyncService syncService = new NpcSpawnSyncService(keys, repository, spawnService);

        plugin.getServer().getPluginManager().registerEvents(new NpcInteractListener(
                spawnService, guiApi, new GuiManager(), questModule.getQuestGuiScreen(), questModule.getProgressService(),
                itemApi, relicApi, economy, plugin.getMessageManager()), plugin);

        NpcAdminService adminService = new NpcAdminService(repository, spawnService, plugin.getConfigManager());
        plugin.getAdminCommandRegistry().register("npc", new NpcAdminCommand(adminService, syncService, plugin.getMessageManager()),
                "NPCの設置・移動・削除を行います。", "npc <create <id> <type> [entityType]|move <id>|remove <id>|list [page]|spawnall>");

        plugin.getAdminCommandRegistry().register("spawnnpc", new NpcSpawnCommand(repository, spawnService),
                "自動配置対象外のNPCを現在地に手動出現させます。", "spawnnpc <npc-id>");

        // NPCs are no longer auto-spawned on startup - run /oladmin npc spawnall to place every
        // configured NPC that isn't already present (safe to re-run any time).
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadNpcs();
    }

    private void reloadNpcs() {
        plugin.getConfigManager().register("npc.yml");
        YamlConfiguration config = plugin.getConfigManager().get("npc.yml").get();
        repository.load(config);
    }

    public NpcRepository getRepository() {
        return repository;
    }

    public NpcSpawnService getSpawnService() {
        return spawnService;
    }
}
