package rpg.extra.achievement;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import rpg.api.SkillApi;
import rpg.api.StatusApi;
import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import rpg.database.manager.DatabaseManager;
import rpg.extra.achievement.command.AchievementCommand;
import rpg.extra.achievement.gui.AchievementGuiScreen;
import rpg.extra.achievement.listener.AchievementJoinListener;
import rpg.extra.achievement.repository.AchievementConfigRepository;
import rpg.extra.achievement.repository.AchievementProgressRepository;
import rpg.extra.achievement.service.AchievementService;
import rpg.gui.framework.GuiManager;
import rpg.world.api.QuestApi;

import java.util.logging.Level;

/**
 * Achievement module: config-driven achievements checked periodically against status/quest/
 * money conditions, with skill-point rewards (SOW AchievementModule).
 */
public final class AchievementModule implements RpgModule {

    private static final long CHECK_PERIOD_TICKS = 20L * 30;

    private final AchievementConfigRepository configRepository = new AchievementConfigRepository();
    private AchievementService achievementService;
    private AchievementGuiScreen guiScreen;
    private GuiManager guiManager;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "achievement";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("achievement module requires OreliaCore's DatabaseManager");
        }
        StatusApi statusApi = plugin.getServer().getServicesManager().load(StatusApi.class);
        if (statusApi == null) {
            throw new IllegalStateException("achievement module requires OreliaCore's StatusApi");
        }
        SkillApi skillApi = plugin.getServer().getServicesManager().load(SkillApi.class);
        if (skillApi == null) {
            throw new IllegalStateException("achievement module requires OreliaCore's SkillApi");
        }
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);
        QuestApi questApi = plugin.getServer().getServicesManager().load(QuestApi.class);

        reloadAchievements();

        AchievementProgressRepository progressRepository = new AchievementProgressRepository(databaseManager);
        try {
            progressRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize achievement schema", e);
        }

        this.achievementService = new AchievementService(configRepository, progressRepository, statusApi, skillApi, economy,
                questApi, plugin.getMessageManager());
        achievementService.loadAll();

        this.guiManager = new GuiManager();
        this.guiScreen = new AchievementGuiScreen(achievementService, guiManager);
        plugin.getServer().getPluginManager().registerEvents(new AchievementJoinListener(achievementService), plugin);
        AchievementCommand achievementCommand = new AchievementCommand(achievementService, guiScreen, guiManager, plugin.getMessageManager());
        plugin.getPlayerCommandRegistry().register("achievement", achievementCommand, "実績一覧を表示します。", "achievement [page|gui]");
        CommandAliasUtil.registerAlias(plugin, "achievement", achievementCommand, "実績一覧を表示します。", "[page|gui]");

        plugin.getSchedulerService().runTimer(achievementService::checkAll, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadAchievements();
    }

    private void reloadAchievements() {
        plugin.getConfigManager().register("achievements.yml");
        YamlConfiguration config = plugin.getConfigManager().get("achievements.yml").get();
        configRepository.load(config);
    }

    public AchievementService getAchievementService() {
        return achievementService;
    }

    /** Opens the same GUI {@code /ol achievement gui} does - see {@link rpg.extra.api.AchievementApi}. */
    public void openGui(Player player) {
        guiManager.open(player, guiScreen.build(player));
    }
}
