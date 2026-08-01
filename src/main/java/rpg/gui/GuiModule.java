package rpg.gui;

import rpg.accessory.AccessoryModule;
import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.database.DatabaseModule;
import rpg.economy.EconomyModule;
import rpg.gui.command.CraftCommand;
import rpg.gui.command.StatusCommand;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.GuiHolder;
import rpg.gui.framework.GuiListener;
import rpg.gui.framework.GuiManager;
import rpg.gui.listener.StatusEquipmentSlotListener;
import rpg.gui.listener.WarehouseSaveListener;
import rpg.gui.repository.WarehouseRepository;
import rpg.gui.screen.CraftingGuiScreen;
import rpg.gui.screen.JobGuiScreen;
import rpg.gui.screen.ShopGuiScreen;
import rpg.gui.screen.SkillGuiScreen;
import rpg.gui.screen.StatusGuiScreen;
import rpg.gui.screen.WarehouseGuiScreen;
import rpg.gui.service.ActionBarService;
import rpg.item.ItemModule;
import rpg.job.JobModule;
import rpg.skill.SkillModule;
import rpg.skill.listener.SkillActivationListener;
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

    private static final long STATUS_REFRESH_PERIOD_TICKS = 20L;

    private final GuiConfig guiConfig = new GuiConfig();
    private OreliaPlugin plugin;
    private GuiManager guiManager;
    private StatusGuiScreen statusGuiScreen;
    private SkillGuiScreen skillGuiScreen;
    private JobGuiScreen jobGuiScreen;
    private ShopGuiScreen shopGuiScreen;
    private WarehouseGuiScreen warehouseGuiScreen;
    private CraftingGuiScreen craftingGuiScreen;
    private ActionBarService actionBarService;

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
        this.statusGuiScreen = new StatusGuiScreen(statusModule.getStatusService(), guiConfig,
                economyModule.getEconomyService(), itemModule.getItemManager().getIdentityService(),
                plugin.getPlayerDataManager());
        this.skillGuiScreen = new SkillGuiScreen(skillModule.getSkillRepository(), skillModule.getProgressService(),
                skillModule.getSocketService(), itemModule.getItemManager().getIdentityService(), guiConfig,
                plugin.getMessageManager());
        this.jobGuiScreen = new JobGuiScreen(jobModule.getJobService(), jobModule.getJobManager(), guiConfig, plugin.getMessageManager());
        this.shopGuiScreen = new ShopGuiScreen(itemModule.getItemManager(), accessoryModule.getRepository(),
                accessoryModule.getFactory(), accessoryModule.getRelicShopService(),
                economyModule.getEconomyService(), guiConfig, plugin.getMessageManager(), guiManager);
        this.craftingGuiScreen = new CraftingGuiScreen(itemModule.getCraftingRepository(), itemModule.getCraftingService(),
                itemModule.getItemManager(), guiConfig, plugin.getMessageManager());

        WarehouseRepository warehouseRepository = new WarehouseRepository(databaseModule.getDatabaseManager());
        try {
            warehouseRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize warehouse schema", e);
        }
        this.warehouseGuiScreen = new WarehouseGuiScreen(warehouseRepository, guiConfig);

        this.actionBarService = new ActionBarService(statusModule.getStatusService(), itemModule.getItemManager().getIdentityService());
        reloadActionBarConfig();
        YamlConfiguration coreConfig = plugin.getConfigManager().get("config.yml").get();
        long actionBarPeriodTicks = coreConfig.getLong("action-bar.period-ticks", 20L);
        plugin.getSchedulerService().runTimer(() ->
                plugin.getServer().getOnlinePlayers().forEach(actionBarService::send),
                actionBarPeriodTicks, actionBarPeriodTicks);

        // Registered here rather than in SkillModule since it needs actionBarService, which
        // doesn't exist until this module enables (see SkillModule.onEnable's comment).
        plugin.getServer().getPluginManager().registerEvents(
                new SkillActivationListener(skillModule.getCastService(), skillModule.getSocketService(),
                        itemModule.getItemManager().getIdentityService(), skillModule.getSkillRepository(),
                        actionBarService, plugin.getMessageManager()), plugin);

        plugin.getServer().getPluginManager().registerEvents(new GuiListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new WarehouseSaveListener(warehouseRepository), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new StatusEquipmentSlotListener(accessoryModule.getIdentityService(),
                        accessoryModule.getRelicIdentityService(), accessoryModule.getEffectService(),
                        accessoryModule.getRelicEffectService(), accessoryModule.getEquipmentRepository(),
                        plugin.getPlayerDataManager(), plugin.getSchedulerService()), plugin);
        StatusCommand statusCommand = new StatusCommand(guiManager, statusGuiScreen, plugin.getMessageManager());
        plugin.getPlayerCommandRegistry().register("status", statusCommand, "ステータス画面を開きます。", "status");
        CommandAliasUtil.registerAlias(plugin, "status", statusCommand, "ステータス画面を開きます。", "");

        CraftCommand craftCommand = new CraftCommand(guiManager, craftingGuiScreen, plugin.getMessageManager());
        plugin.getPlayerCommandRegistry().register("craft", craftCommand, "合成画面を開きます。", "craft");
        CommandAliasUtil.registerAlias(plugin, "craft", craftCommand, "合成画面を開きます。", "");

        // Stats can change from many unrelated sources (level-up, buffs, held-weapon swap) while
        // this screen is open - periodic refresh is simpler than hooking every mutation site.
        // The 6 equip slots are excluded from that refresh (see StatusGuiScreen#refresh); their
        // only mutation site is StatusEquipmentSlotListener, which repaints them itself.
        plugin.getSchedulerService().runTimer(() ->
                plugin.getServer().getOnlinePlayers().forEach(player -> {
                    var top = player.getOpenInventory().getTopInventory();
                    if (top.getHolder() instanceof GuiHolder holder && StatusGuiScreen.TAG.equals(holder.getGui().getTag())) {
                        statusGuiScreen.refresh(player, top);
                    }
                }),
                STATUS_REFRESH_PERIOD_TICKS, STATUS_REFRESH_PERIOD_TICKS);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadGuiConfig();
        reloadActionBarConfig();
    }

    /** period-ticks is only read once at startup (same limitation StatusModule's regen tick has). */
    private void reloadActionBarConfig() {
        YamlConfiguration coreConfig = plugin.getConfigManager().get("config.yml").get();
        actionBarService.setEnabled(coreConfig.getBoolean("action-bar.enabled", true));
        actionBarService.setFormat(coreConfig.getString("action-bar.format", ""));
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

    public CraftingGuiScreen getCraftingGuiScreen() {
        return craftingGuiScreen;
    }

    public ActionBarService getActionBarService() {
        return actionBarService;
    }
}
