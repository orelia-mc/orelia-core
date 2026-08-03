package rpg.extra.guild;

import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import rpg.database.manager.DatabaseManager;
import rpg.extra.guild.command.GuildCommand;
import rpg.extra.guild.listener.GuildQuitListener;
import rpg.extra.guild.listener.NpcGuildInteractListener;
import rpg.extra.guild.manager.GuildManager;
import rpg.extra.guild.repository.GuildRepository;
import rpg.extra.guild.service.GuildService;

import java.util.logging.Level;

/**
 * Guild module: persistent player organizations with leader/officer/member roles (SOW
 * GuildModule).
 */
public final class GuildModule implements RpgModule {

    private GuildService guildService;

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

        plugin.getServer().getPluginManager().registerEvents(new GuildQuitListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new NpcGuildInteractListener(guildService, plugin.getMessageManager()), plugin);
        GuildCommand guildCommand = new GuildCommand(guildService, plugin.getMessageManager());
        String description = "ギルドを管理します。";
        String usage = "guild <create|invite|accept|leave|kick|promote|demote|disband|transfer|list|info|chat <message>>";
        plugin.getPlayerCommandRegistry().register("guild", guildCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "guild", guildCommand, description,
                "<create|invite|accept|leave|kick|promote|demote|disband|transfer|list|info|chat <message>>");
    }

    @Override
    public void onDisable() {
    }

    public GuildService getGuildService() {
        return guildService;
    }
}
