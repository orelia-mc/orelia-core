package rpg.core;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import rpg.core.command.AdminCommand;
import rpg.core.command.AdminCommandRegistry;
import rpg.core.command.OlRootCommand;
import rpg.core.command.PlayerCommandRegistry;
import rpg.core.config.ConfigManager;
import rpg.core.listener.PlayerConnectionListener;
import rpg.core.module.ModuleManager;
import rpg.core.player.PlayerDataManager;
import rpg.core.scheduler.SchedulerService;
import rpg.database.DatabaseModule;
import rpg.status.StatusModule;
import rpg.job.JobModule;
import rpg.gathering.GatheringModule;
import rpg.item.ItemModule;
import rpg.skill.SkillModule;
import rpg.accessory.AccessoryModule;
import rpg.effect.EffectModule;
import rpg.economy.EconomyModule;
import rpg.monster.MonsterModule;
import rpg.boss.BossModule;
import rpg.gui.GuiModule;
import rpg.api.ApiModule;

/**
 * Plugin entry point for the orelia-core repo/jar. Owns process-wide singletons (config,
 * player data, scheduler, module registry) and wires every top-level Module in dependency
 * order. No gameplay logic lives here; see the individual module packages.
 *
 * <p>orelia-world and orelia-extra are separate plugins/jars built from separate repos.
 * They depend on this plugin ({@code depend: [OreliaCore]} in their own plugin.yml) and
 * talk to it only through {@link rpg.api}, published via Bukkit's {@code ServicesManager} -
 * never by reaching into these module classes directly.
 */
public final class OreliaPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private SchedulerService schedulerService;
    private PlayerDataManager playerDataManager;
    private ModuleManager moduleManager;
    private PlayerCommandRegistry playerCommandRegistry;
    private AdminCommandRegistry adminCommandRegistry;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.register("config.yml");

        this.schedulerService = new SchedulerService(this);
        this.playerDataManager = new PlayerDataManager(getLogger(), schedulerService);
        this.moduleManager = new ModuleManager(this);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(playerDataManager), this);

        // Published so orelia-world/orelia-extra can register their own subcommands into
        // these same two short entry points instead of each claiming a top-level command.
        this.playerCommandRegistry = new PlayerCommandRegistry();
        this.adminCommandRegistry = new AdminCommandRegistry();
        getServer().getServicesManager().register(PlayerCommandRegistry.class, playerCommandRegistry, this, ServicePriority.Normal);
        getServer().getServicesManager().register(AdminCommandRegistry.class, adminCommandRegistry, this, ServicePriority.Normal);

        OlRootCommand olRootCommand = new OlRootCommand(playerCommandRegistry);
        getCommand("ol").setExecutor(olRootCommand);
        getCommand("ol").setTabCompleter(olRootCommand);

        AdminCommand adminCommand = new AdminCommand(this, adminCommandRegistry);
        getCommand("oladmin").setExecutor(adminCommand);
        getCommand("oladmin").setTabCompleter(adminCommand);

        // Registration order doubles as dependency order: later modules may look up
        // earlier ones via ModuleManager#get, never the reverse. ApiModule is always last
        // so every service it publishes is fully constructed first.
        moduleManager.register(new DatabaseModule());
        moduleManager.register(new StatusModule());
        moduleManager.register(new JobModule());
        moduleManager.register(new GatheringModule());
        moduleManager.register(new ItemModule());
        moduleManager.register(new SkillModule());
        moduleManager.register(new AccessoryModule());
        moduleManager.register(new EffectModule());
        moduleManager.register(new EconomyModule());
        moduleManager.register(new MonsterModule());
        moduleManager.register(new BossModule());
        moduleManager.register(new GuiModule());
        moduleManager.register(new ApiModule());

        moduleManager.enableAll();
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllOnlineSync();
        }
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
    }

    public void reload() {
        configManager.reloadAll();
        moduleManager.reloadAll();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SchedulerService getSchedulerService() {
        return schedulerService;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public PlayerCommandRegistry getPlayerCommandRegistry() {
        return playerCommandRegistry;
    }

    public AdminCommandRegistry getAdminCommandRegistry() {
        return adminCommandRegistry;
    }
}
