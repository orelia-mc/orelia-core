package rpg.extra.mail;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.command.CommandAliasUtil;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.mail.command.MailCommand;
import rpg.extra.mail.config.MailConfig;
import rpg.extra.mail.gui.MailGuiScreen;
import rpg.extra.mail.listener.MailUnreadJoinListener;
import rpg.extra.mail.repository.MailRepository;
import rpg.extra.mail.service.MailService;
import rpg.gui.framework.GuiManager;

import java.util.logging.Level;

/**
 * Mail module: persisted mailbox with item attachments (SOW MailModule).
 */
public final class MailModule implements RpgModule {

    private final MailConfig mailConfig = new MailConfig();
    private MailService mailService;
    private MailGuiScreen guiScreen;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "mail";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("mail module requires OreliaCore's DatabaseManager");
        }

        MailRepository repository = new MailRepository(databaseManager);
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize mail schema", e);
        }

        reloadMailConfig();
        this.mailService = new MailService(repository, mailConfig);
        GuiManager guiManager = new GuiManager();
        this.guiScreen = new MailGuiScreen(mailService, guiManager, plugin.getMessageManager());

        MailCommand mailCommand = new MailCommand(mailService, guiScreen, guiManager, plugin.getMessageManager());
        plugin.getPlayerCommandRegistry().register("mail", mailCommand,
                "郵便受けを開きます。", "mail [unread|send <player> <subject...>|delete <index>]");
        CommandAliasUtil.registerAlias(plugin, "mail", mailCommand,
                "郵便受けを開きます。", "[unread|send <player> <subject...>|delete <index>]");

        plugin.getServer().getPluginManager().registerEvents(
                new MailUnreadJoinListener(mailService, plugin.getMessageManager(), plugin.getSchedulerService(),
                        mailConfig, plugin.getLogger(), plugin.getChatMuteService()),
                plugin);

        plugin.getSchedulerService().runTimer(mailService::purgeExpired,
                mailConfig.getPurgeCheckPeriodTicks(), mailConfig.getPurgeCheckPeriodTicks());
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadMailConfig();
    }

    private void reloadMailConfig() {
        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        mailConfig.load(config);
    }

    public MailService getMailService() {
        return mailService;
    }

    public MailGuiScreen getGuiScreen() {
        return guiScreen;
    }
}
