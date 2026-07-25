package rpg.economy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.DatabaseModule;
import rpg.economy.repository.EconomyRepository;
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

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            // High priority so Orelia's own balance wins ServicesManager.load(Economy.class)
            // lookups (e.g. PetService/MountService unlock checks) over any other Economy
            // provider that might also be registered at the default Normal priority.
            Bukkit.getServicesManager().register(
                    net.milkbowl.vault.economy.Economy.class,
                    new OreliaVaultEconomy(economyService),
                    plugin,
                    ServicePriority.High);
            plugin.getLogger().info("Registered Orelia as the Vault economy provider.");
        }
    }

    @Override
    public void onDisable() {
    }

    public EconomyService getEconomyService() {
        return economyService;
    }
}
