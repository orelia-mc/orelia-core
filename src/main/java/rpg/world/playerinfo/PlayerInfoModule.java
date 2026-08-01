package rpg.world.playerinfo;

import org.bukkit.plugin.ServicesManager;
import rpg.api.GuiApi;
import rpg.api.JobApi;
import rpg.api.SkillApi;
import rpg.extra.api.AchievementApi;
import rpg.gui.framework.GuiManager;
import rpg.quest.QuestModule;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.world.playerinfo.gui.PlayerInfoGuiScreen;
import rpg.world.playerinfo.listener.PlayerInfoItemListener;
import rpg.world.playerinfo.service.PlayerInfoItemKeys;
import rpg.world.playerinfo.service.PlayerInfoItemService;

/**
 * The nether-star "プレイヤー情報" menu: quests come from orelia-world's own quest module,
 * job/skill come from orelia-core through {@link JobApi}/{@link SkillApi}.
 */
public final class PlayerInfoModule implements RpgModule {

    @Override
    public String getName() {
        return "playerinfo";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        ServicesManager services = plugin.getServer().getServicesManager();
        JobApi jobApi = services.load(JobApi.class);
        SkillApi skillApi = services.load(SkillApi.class);
        GuiApi guiApi = services.load(GuiApi.class);
        if (jobApi == null || skillApi == null || guiApi == null) {
            throw new IllegalStateException("playerinfo module requires OreliaCore's JobApi, SkillApi and GuiApi");
        }
        // Soft dependency - null when OreliaExtra isn't installed, guarded in PlayerInfoGuiScreen.
        AchievementApi achievementApi = services.load(AchievementApi.class);

        QuestModule questModule = plugin.getModuleManager().get(QuestModule.class)
                .orElseThrow(() -> new IllegalStateException("playerinfo module requires quest module"));

        GuiManager guiManager = new GuiManager();
        PlayerInfoItemService itemService = new PlayerInfoItemService(new PlayerInfoItemKeys(plugin));
        PlayerInfoGuiScreen guiScreen = new PlayerInfoGuiScreen(
                questModule.getQuestRepository(), plugin.getPlayerDataManager(), jobApi, skillApi, guiApi,
                achievementApi, guiManager);

        plugin.getServer().getPluginManager().registerEvents(
                new PlayerInfoItemListener(itemService, guiScreen, guiManager), plugin);
    }

    @Override
    public void onDisable() {
    }
}
