package rpg.gui;

import rpg.accessory.AccessoryModule;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.database.DatabaseModule;
import rpg.economy.EconomyModule;
import rpg.gui.command.StatusCommand;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.GuiListener;
import rpg.gui.framework.GuiManager;
import rpg.gui.listener.WarehouseSaveListener;
import rpg.gui.repository.WarehouseRepository;
import rpg.gui.screen.EquipmentGuiScreen;
import rpg.gui.screen.JobGuiScreen;
import rpg.gui.screen.ShopGuiScreen;
import rpg.gui.screen.SkillGuiScreen;
import rpg.gui.screen.StatusGuiScreen;
import rpg.gui.screen.WarehouseGuiScreen;
import rpg.item.ItemModule;
import rpg.job.JobModule;
import rpg.skill.SkillModule;
import rpg.status.StatusModule;

import java.util.logging.Level;

/**
 * GUI module: the single place inventory-screen framework code and every core-owned
 * screen implementation lives (SOW coding rule "GUI処理はGUIパッケージへ実装する"). Other
 * core modules call the screen classes exposed here directly; orelia-world calls them
 * through {@link rpg.api.GuiApi}. The quest screen is NOT here - it belongs to
 * orelia-world's own QuestModule since Quest is a content-layer concern in the new
 * 3-repo split.
 */
public final class GuiModule implements RpgModule {

    private final GuiConfig guiConfig = new GuiConfig();
    private OreliaPlugin plugin;
    private GuiManager guiManager;
    private StatusGuiScreen statusGuiScreen;
    private EquipmentGuiScreen equipmentGuiScreen;
    private SkillGuiScreen skillGuiScreen;
    private JobGuiScreen jobGuiScreen;
    private ShopGuiScreen shopGuiScreen;
    private WarehouseGuiScreen warehouseGuiScreen;

    @Override
    public String getName() {
        return "gui";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        reloadGuiConfig();
        DatabaseModule databaseModule = require(plugin, DatabaseModule.class);
        StatusModule statusModule = require(plugin, StatusModule.class);
        JobModule jobModule = require(plugin, JobModule.class);
        ItemModule itemModule = require(plugin, ItemModule.class);
        SkillModule skillModule = require(plugin, SkillModule.class);
        AccessoryModule accessoryModule = require(plugin, AccessoryModule.class);
        EconomyModule economyModule = require(plugin, EconomyModule.class);

        this.guiManager = new GuiManager();
        this.statusGuiScreen = new StatusGuiScreen(statusModule.getStatusService(), guiConfig);
        this.equipmentGuiScreen = new EquipmentGuiScreen(guiConfig);
        this.skillGuiScreen = new SkillGuiScreen(skillModule.getSkillRepository(), skillModule.getProgressService(),
                skillModule.getSocketService(), itemModule.getItemManager().getIdentityService(), guiConfig,
                plugin.getMessageManager());
        this.jobGuiScreen = new JobGuiScreen(jobModule.getJobService(), jobModule.getJobManager(), guiConfig, plugin.getMessageManager());
        this.shopGuiScreen = new ShopGuiScreen(itemModule.getItemManager(), accessoryModule.getRepository(),
                accessoryModule.getFactory(), economyModule.getEconomyService(), guiConfig, plugin.getMessageManager());

        WarehouseRepository warehouseRepository = new WarehouseRepository(databaseModule.getDatabaseManager());
        try {
            warehouseRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize warehouse schema", e);
        }
        this.warehouseGuiScreen = new WarehouseGuiScreen(warehouseRepository, guiConfig);

        plugin.getServer().getPluginManager().registerEvents(new GuiListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new WarehouseSaveListener(warehouseRepository), plugin);
        plugin.getPlayerCommandRegistry().register("status",
                new StatusCommand(guiManager, statusGuiScreen, plugin.getMessageManager()),
                "ステータス画面を開きます。", "status");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadGuiConfig();
    }

    private void reloadGuiConfig() {
        plugin.getConfigManager().register("gui.yml");
        YamlConfiguration config = plugin.getConfigManager().get("gui.yml").get();
        guiConfig.load(config);
    }

    private <T extends RpgModule> T require(OreliaPlugin plugin, Class<T> type) {
        return plugin.getModuleManager().get(type)
                .orElseThrow(() -> new IllegalStateException("gui module requires " + type.getSimpleName()));
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public StatusGuiScreen getStatusGuiScreen() {
        return statusGuiScreen;
    }

    public EquipmentGuiScreen getEquipmentGuiScreen() {
        return equipmentGuiScreen;
    }

    public SkillGuiScreen getSkillGuiScreen() {
        return skillGuiScreen;
    }

    public JobGuiScreen getJobGuiScreen() {
        return jobGuiScreen;
    }

    public ShopGuiScreen getShopGuiScreen() {
        return shopGuiScreen;
    }

    public WarehouseGuiScreen getWarehouseGuiScreen() {
        return warehouseGuiScreen;
    }
}
