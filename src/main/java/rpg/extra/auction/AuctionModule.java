package rpg.extra.auction;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.command.CommandAliasUtil;
import rpg.database.manager.DatabaseManager;
import rpg.extra.auction.command.AuctionCommand;
import rpg.extra.auction.config.AuctionConfig;
import rpg.extra.auction.gui.AuctionGuiScreen;
import rpg.extra.auction.repository.AuctionRepository;
import rpg.extra.auction.service.AuctionService;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.mail.MailModule;
import rpg.gui.framework.GuiManager;

import java.util.logging.Level;

/**
 * Auction module: player-run auction house with timed listings (SOW AuctionModule). Money
 * settles through Vault's {@link Economy}.
 */
public final class AuctionModule implements RpgModule {

    private final AuctionConfig auctionConfig = new AuctionConfig();
    private AuctionService auctionService;
    private AuctionGuiScreen guiScreen;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "auction";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("auction module requires OreliaCore's DatabaseManager");
        }
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);
        if (economy == null) {
            throw new IllegalStateException("auction module requires Vault's Economy service");
        }
        MailModule mailModule = plugin.getModuleManager().get(MailModule.class)
                .orElseThrow(() -> new IllegalStateException("auction module requires mail module"));

        AuctionRepository repository = new AuctionRepository(databaseManager);
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize auction schema", e);
        }

        reloadAuctionConfig();
        this.auctionService = new AuctionService(repository, economy, mailModule.getMailService(),
                plugin.getMessageManager(), auctionConfig);
        auctionService.loadAll();

        GuiManager guiManager = new GuiManager();
        this.guiScreen = new AuctionGuiScreen(auctionService, guiManager, plugin.getMessageManager());
        AuctionCommand auctionCommand = new AuctionCommand(auctionService, guiScreen, guiManager, plugin.getMessageManager());
        String description = "オークションを利用します。";
        String usage = "auction [list|sell <price>|start <startPrice> [hours]|bid <id> <amount>|collect]";
        plugin.getPlayerCommandRegistry().register("auction", auctionCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "auction", auctionCommand, description, usage);

        plugin.getSchedulerService().runTimer(auctionService::expireOverdueListings,
                auctionConfig.getExpiryCheckPeriodTicks(), auctionConfig.getExpiryCheckPeriodTicks());
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadAuctionConfig();
    }

    private void reloadAuctionConfig() {
        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        auctionConfig.load(config);
    }

    public AuctionService getAuctionService() {
        return auctionService;
    }

    public AuctionGuiScreen getGuiScreen() {
        return guiScreen;
    }
}
