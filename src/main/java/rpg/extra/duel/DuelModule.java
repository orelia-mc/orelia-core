package rpg.extra.duel;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.api.StatusApi;
import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import rpg.database.manager.DatabaseManager;
import rpg.extra.duel.command.DuelArenaAdminCommand;
import rpg.extra.duel.command.DuelCommand;
import rpg.extra.duel.gui.DuelGuiScreen;
import rpg.extra.duel.gui.DuelRankingGuiScreen;
import rpg.extra.duel.listener.DuelDamageListener;
import rpg.extra.duel.listener.DuelQuitListener;
import rpg.extra.duel.manager.DuelRequestManager;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.repository.DuelArenaRepository;
import rpg.extra.duel.repository.DuelStatsRepository;
import rpg.extra.duel.service.DuelArenaAdminService;
import rpg.extra.duel.service.DuelService;
import rpg.extra.duel.service.DuelStatsService;
import rpg.gui.framework.GuiManager;

import java.util.logging.Level;

/**
 * Duel module: 1v1 duel requests, multi-arena registration, HP-threshold duel resolution with
 * no real death, a small server-funded money reward, and a simple win/loss leaderboard (SOW
 * follow-up, see docs/superpowers/specs/2026-08-30-duel-module-design.md).
 */
public final class DuelModule implements RpgModule {

    private final DuelArenaRepository arenaRepository = new DuelArenaRepository();
    private final DuelRequestManager requestManager = new DuelRequestManager();
    private final DuelSessionManager sessionManager = new DuelSessionManager(arenaRepository);
    private final GuiManager guiManager = new GuiManager();
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "duel";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("duel module requires OreliaCore's DatabaseManager");
        }
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);
        if (economy == null) {
            throw new IllegalStateException("duel module requires Vault's Economy service");
        }
        StatusApi statusApi = plugin.getServer().getServicesManager().load(StatusApi.class);
        if (statusApi == null) {
            throw new IllegalStateException("duel module requires OreliaCore's StatusApi");
        }

        reloadArenas();

        DuelStatsRepository statsRepository = new DuelStatsRepository(databaseManager);
        try {
            statsRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize duel schema", e);
        }
        DuelStatsService statsService = new DuelStatsService(statsRepository);

        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        long cooldownSeconds = config.getLong("duel.cooldown-seconds", 60);
        double rewardMoney = config.getDouble("duel.reward-money", 50);

        DuelService duelService = new DuelService(requestManager, sessionManager, cooldownSeconds);
        DuelDamageListener damageListener = new DuelDamageListener(sessionManager, statsService, statusApi, economy,
                plugin.getMessageManager(), rewardMoney);
        DuelQuitListener quitListener = new DuelQuitListener(sessionManager, requestManager, damageListener);
        plugin.getServer().getPluginManager().registerEvents(damageListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(quitListener, plugin);

        DuelGuiScreen guiScreen = new DuelGuiScreen(requestManager, duelService, plugin.getMessageManager());
        DuelRankingGuiScreen rankingScreen = new DuelRankingGuiScreen(statsService);
        DuelCommand duelCommand = new DuelCommand(duelService, sessionManager, damageListener, guiScreen, rankingScreen,
                guiManager, plugin.getMessageManager());
        String description = "決闘画面を開きます。";
        String usage = "duel [gui|request <player>|accept [player]|decline [player]|cancel <player>|forfeit|ranking]";
        plugin.getPlayerCommandRegistry().register("duel", duelCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "duel", duelCommand, description,
                "[gui|request <player>|accept [player]|decline [player]|cancel <player>|forfeit|ranking]");

        DuelArenaAdminService arenaAdminService = new DuelArenaAdminService(arenaRepository, plugin.getConfigManager());
        DuelArenaAdminCommand arenaAdminCommand = new DuelArenaAdminCommand(arenaAdminService, plugin.getMessageManager());
        plugin.getAdminCommandRegistry().register("duelarena", arenaAdminCommand,
                "決闘アリーナを追加・削除します。", "duelarena add|set <index>|remove <index>|list");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadArenas();
    }

    private void reloadArenas() {
        plugin.getConfigManager().register("duels.yml");
        YamlConfiguration config = plugin.getConfigManager().get("duels.yml").get();
        arenaRepository.load(config);
    }
}
