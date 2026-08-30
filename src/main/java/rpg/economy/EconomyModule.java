package rpg.economy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.DatabaseModule;
import rpg.economy.repository.BankRepository;
import rpg.economy.repository.EconomyRepository;
import rpg.economy.service.BankService;
import rpg.economy.service.EconomyService;
import rpg.economy.vault.OreliaVaultEconomy;

import java.util.logging.Level;

/**
 * Wallet/currency module. Registers an {@code Economy} provider with Vault when Vault is
 * present, so shop-style plugins can interact with Orelia balances without a hard
 * dependency on this plugin's classes.
 */
public final class EconomyModule implements RpgModule {

    private EconomyService economyService;
    private BankService bankService;

    @Override
    public String getName() {
        return "economy";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        DatabaseModule databaseModule = plugin.getModuleManager().get(DatabaseModule.class)
                .orElseThrow(() -> new IllegalStateException("economy module requires database module"));

        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        double startingBalance = config.getDouble("economy.starting-balance", 100.0);

        EconomyRepository repository = new EconomyRepository(databaseModule.getDatabaseManager(), startingBalance);
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize economy schema", e);
        }

        this.economyService = new EconomyService(repository);

        BankRepository bankRepository = new BankRepository(databaseModule.getDatabaseManager());
        try {
            bankRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize bank schema", e);
        }
        this.bankService = new BankService(bankRepository);

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            // Highest priority (the top of ServicePriority, not just "High") so Orelia's own
            // balance always wins ServicesManager.load(Economy.class) lookups (e.g.
            // PetService/HousingService/AuctionService/MountService unlock/purchase checks)
            // over any other Economy provider on the server, regardless of what priority that
            // other provider registered at. Previously this used ServicePriority.High, which
            // only beats a competitor at the default Normal priority - a second economy plugin
            // registering at High or Highest would silently win the lookup instead, so those
            // purchase checks would run against a balance the player never sees anywhere in
            // Orelia's own UI (e.g. the status GUI's money display, which always reads
            // EconomyService directly rather than going through Vault) - looking exactly like
            // "I have enough money but it still says insufficient funds".
            Bukkit.getServicesManager().register(
                    net.milkbowl.vault.economy.Economy.class,
                    new OreliaVaultEconomy(economyService, bankService),
                    plugin,
                    ServicePriority.Highest);
            plugin.getLogger().info("Registered Orelia as the Vault economy provider.");
        }
    }

    @Override
    public void onDisable() {
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public BankService getBankService() {
        return bankService;
    }
}
