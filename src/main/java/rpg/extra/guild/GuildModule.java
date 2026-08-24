package rpg.extra.guild;

import rpg.core.command.CommandAliasUtil;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.guild.command.GuildCommand;
import rpg.extra.guild.gui.GuildGuiScreen;
import rpg.extra.guild.listener.GuildQuitListener;
import rpg.extra.guild.listener.NpcGuildInteractListener;
import rpg.extra.guild.manager.GuildManager;
import rpg.extra.guild.repository.GuildRepository;
import rpg.extra.guild.service.GuildService;
import rpg.gui.framework.GuiManager;

import java.util.logging.Level;

/**
 * Guild module: persistent player organizations with leader/officer/member roles (SOW
 * GuildModule).
 */
public final class GuildModule implements RpgModule {

    private GuildService guildService;
    private GuildGuiScreen guildGuiScreen;
    private GuiManager guiManager;

    @Override
    public String getName() {
        return "guild";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("guild module requires OreliaCore's DatabaseManager");
        }

        GuildRepository repository = new GuildRepository(databaseManager);
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize guild schema", e);
        }

        GuildManager manager = new GuildManager(repository);
        manager.loadAll();

        this.guildService = new GuildService(manager);
        this.guiManager = new GuiManager();
        this.guildGuiScreen = new GuildGuiScreen(guildService, guiManager, plugin.getChatInputService(), plugin.getMessageManager());

        plugin.getServer().getPluginManager().registerEvents(new GuildQuitListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new NpcGuildInteractListener(guildService, plugin.getMessageManager()), plugin);
        GuildCommand guildCommand = new GuildCommand(guildService, plugin.getMessageManager(), plugin.getChatMuteService(),
                guildGuiScreen, guiManager, plugin.getConfigManager(), plugin.getLogger());
        String description = "ギルドを管理します。";
        String usage = "guild <create|invite|accept|leave|kick|promote|demote|disband|transfer|list|info|gui|chat <message>>";
        plugin.getPlayerCommandRegistry().register("guild", guildCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "guild", guildCommand, description,
                "<create|invite|accept|leave|kick|promote|demote|disband|transfer|list|info|gui|chat <message>>");
    }

    @Override
    public void onDisable() {
    }

    public GuildGuiScreen getGuildGuiScreen() {
        return guildGuiScreen;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public GuildService getGuildService() {
        return guildService;
    }
}
