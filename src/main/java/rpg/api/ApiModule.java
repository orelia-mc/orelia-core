package rpg.api;

import org.bukkit.plugin.ServicePriority;
import rpg.accessory.AccessoryModule;
import rpg.boss.BossModule;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.core.player.PlayerDataManager;
import rpg.database.DatabaseModule;
import rpg.database.manager.DatabaseManager;
import rpg.economy.EconomyModule;
import rpg.effect.EffectModule;
import rpg.gui.GuiModule;
import rpg.item.ItemModule;
import rpg.job.JobModule;
import rpg.monster.MonsterModule;
import rpg.skill.SkillModule;
import rpg.status.StatusModule;

/**
 * Publishes every cross-plugin API interface to Bukkit's {@code ServicesManager}. This is
 * the ONLY door orelia-world (and orelia-extra) are meant to use to reach into
 * orelia-core - see {@link OreliaApi}, {@link StatusApi}, {@link JobApi}, {@link ItemApi},
 * {@link AccessoryApi}, {@link SkillApi}, {@link GuiApi}, {@link EffectApi},
 * {@link CombatApi} (SOW section 19 / "APIを通してModule間・リポジトリ間の連携を行い、
 * 直接依存を避ける"). Registered last so every service it wraps is already fully constructed.
 *
 * <p>{@link PlayerDataManager} and {@link DatabaseManager} are also published as-is: they
 * are generic per-plugin/per-player infrastructure (not gameplay logic), so orelia-world's
 * own modules register their own {@code PlayerDataComponentLoader}s / own SQL tables
 * through them directly, exactly like a core module would.
 */
public final class ApiModule implements RpgModule {

    private OreliaApi api;

    @Override
    public String getName() {
        return "api";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        DatabaseModule databaseModule = require(plugin, DatabaseModule.class);
        StatusModule statusModule = require(plugin, StatusModule.class);
        JobModule jobModule = require(plugin, JobModule.class);
        ItemModule itemModule = require(plugin, ItemModule.class);
        AccessoryModule accessoryModule = require(plugin, AccessoryModule.class);
        SkillModule skillModule = require(plugin, SkillModule.class);
        EffectModule effectModule = require(plugin, EffectModule.class);
        EconomyModule economyModule = require(plugin, EconomyModule.class);
        MonsterModule monsterModule = require(plugin, MonsterModule.class);
        BossModule bossModule = require(plugin, BossModule.class);
        GuiModule guiModule = require(plugin, GuiModule.class);

        this.api = new OreliaApiImpl(plugin);

        var servicesManager = plugin.getServer().getServicesManager();
        servicesManager.register(OreliaApi.class, api, plugin, ServicePriority.Normal);
        servicesManager.register(StatusApi.class, new StatusApiImpl(statusModule.getStatusService()), plugin, ServicePriority.Normal);
        servicesManager.register(JobApi.class, new JobApiImpl(jobModule.getJobService(), jobModule.getJobManager()), plugin, ServicePriority.Normal);
        servicesManager.register(ItemApi.class, new ItemApiImpl(itemModule.getItemManager()), plugin, ServicePriority.Normal);
        servicesManager.register(AccessoryApi.class,
                new AccessoryApiImpl(accessoryModule.getRepository(), accessoryModule.getFactory()), plugin, ServicePriority.Normal);
        servicesManager.register(SkillApi.class,
                new SkillApiImpl(skillModule.getProgressService(), skillModule.getSkillRepository()), plugin, ServicePriority.Normal);
        servicesManager.register(EffectApi.class, new EffectApiImpl(effectModule.getPlaybackService()), plugin, ServicePriority.Normal);
        servicesManager.register(EconomyApi.class, new EconomyApiImpl(economyModule.getEconomyService()), plugin, ServicePriority.Normal);
        servicesManager.register(DebugApi.class, new DebugApiImpl(plugin.getConfigManager()), plugin, ServicePriority.Normal);
        servicesManager.register(CombatApi.class,
                new CombatApiImpl(monsterModule.getSpawnService(), bossModule.getRepository()), plugin, ServicePriority.Normal);
        servicesManager.register(GuiApi.class, new GuiApiImpl(guiModule), plugin, ServicePriority.Normal);
        servicesManager.register(PlayerDataManager.class, plugin.getPlayerDataManager(), plugin, ServicePriority.Normal);
        servicesManager.register(DatabaseManager.class, databaseModule.getDatabaseManager(), plugin, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
    }

    private <T extends RpgModule> T require(OreliaPlugin plugin, Class<T> type) {
        return plugin.getModuleManager().get(type)
                .orElseThrow(() -> new IllegalStateException("api module requires " + type.getSimpleName()));
    }

    public OreliaApi getApi() {
        return api;
    }
}
