package rpg.extra.trade;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.command.CommandAliasUtil;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.trade.command.TradeCommand;
import rpg.extra.trade.config.TradeConfig;
import rpg.extra.trade.listener.TradeQuitListener;
import rpg.extra.trade.manager.TradeManager;
import rpg.extra.trade.repository.TradeLogRepository;
import rpg.extra.trade.service.TradeService;

import java.util.logging.Level;

/**
 * Trade module: two-player item (and optionally money) trading with a confirm/confirm
 * handshake (SOW TradeModule).
 */
public final class TradeModule implements RpgModule {

    private final TradeManager manager = new TradeManager();
    private final TradeConfig tradeConfig = new TradeConfig();
    private TradeService tradeService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "trade";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("trade module requires OreliaCore's DatabaseManager");
        }
        // Soft dependency - money trading is simply unavailable (MONEY_UNSUPPORTED) if
        // Vault isn't installed, same as Auction's own hard requirement is not appropriate
        // here since item-only trading is still fully useful without it.
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);

        TradeLogRepository logRepository = new TradeLogRepository(databaseManager);
        try {
            logRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize trade log schema", e);
        }

        reloadTradeConfig();
        this.tradeService = new TradeService(manager, economy, plugin.getSchedulerService(), tradeConfig,
                plugin.getMessageManager(), logRepository);
        plugin.getServer().getPluginManager().registerEvents(new TradeQuitListener(tradeService, plugin.getMessageManager()), plugin);
        TradeCommand tradeCommand = new TradeCommand(tradeService, plugin.getMessageManager(), tradeConfig, plugin.getLogger(),
                plugin.getChatMuteService());
        String description = "他プレイヤーとアイテムを取引します。";
        String usage = "trade <player>|accept|add|remove <index>|money <amount>|confirm|cancel|view";
        plugin.getPlayerCommandRegistry().register("trade", tradeCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "trade", tradeCommand, description,
                "<player>|accept|add|remove <index>|money <amount>|confirm|cancel|view");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadTradeConfig();
    }

    private void reloadTradeConfig() {
        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        tradeConfig.load(config);
    }

    public TradeService getTradeService() {
        return tradeService;
    }
}
