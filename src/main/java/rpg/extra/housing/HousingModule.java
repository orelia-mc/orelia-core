package rpg.extra.housing;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.housing.command.HousingCommand;
import rpg.extra.housing.gui.HousingGuiScreen;
import rpg.extra.housing.repository.HouseOwnershipRepository;
import rpg.extra.housing.repository.HousePlotRepository;
import rpg.extra.housing.service.HousingService;
import rpg.gui.framework.GuiManager;

import java.util.logging.Level;

/**
 * Housing module: config-driven purchasable house plots with a {@code /ol house home} teleport
 * (SOW HousingModule). Money settles through Vault's {@link Economy}.
 */
public final class HousingModule implements RpgModule {

    private final HousePlotRepository plotRepository = new HousePlotRepository();
    private final GuiManager guiManager = new GuiManager();
    private HousingService housingService;
    private HousingGuiScreen guiScreen;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "housing";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("housing module requires OreliaCore's DatabaseManager");
        }
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);
        if (economy == null) {
            throw new IllegalStateException("housing module requires Vault's Economy service");
        }

        reloadPlots();

        HouseOwnershipRepository ownershipRepository = new HouseOwnershipRepository(databaseManager);
        try {
            ownershipRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize housing schema", e);
        }

        this.housingService = new HousingService(plotRepository, ownershipRepository, economy);
        housingService.loadAll();
        this.guiScreen = new HousingGuiScreen(housingService, plugin.getMessageManager());

        plugin.getPlayerCommandRegistry().register("house",
                new HousingCommand(housingService, guiScreen, guiManager, plugin.getMessageManager()),
                "自宅の購入・移動を行います。", "house [list|gui|buy <plotId>|home]");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadPlots();
    }

    private void reloadPlots() {
        plugin.getConfigManager().register("housing.yml");
        YamlConfiguration config = plugin.getConfigManager().get("housing.yml").get();
        plotRepository.load(config);
    }

    public HousingService getHousingService() {
        return housingService;
    }

    public HousePlotRepository getPlotRepository() {
        return plotRepository;
    }

    public HousingGuiScreen getGuiScreen() {
        return guiScreen;
    }
}
