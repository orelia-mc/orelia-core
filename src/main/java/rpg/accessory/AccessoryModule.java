package rpg.accessory;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.accessory.listener.AccessoryEquipmentJoinListener;
import rpg.accessory.manager.AccessoryEquipmentManager;
import rpg.accessory.repository.AccessoryEquipmentRepository;
import rpg.accessory.repository.AccessoryRepository;
import rpg.accessory.service.AccessoryEffectService;
import rpg.accessory.service.AccessoryFactory;
import rpg.accessory.service.AccessoryIdentityService;
import rpg.accessory.service.AccessoryKeys;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.DatabaseModule;
import rpg.gui.framework.GuiManager;
import rpg.relic.command.RelicCommand;
import rpg.relic.config.RelicConfig;
import rpg.relic.gui.RelicUpgradeGuiScreen;
import rpg.relic.service.RelicEffectService;
import rpg.relic.service.RelicFactory;
import rpg.relic.service.RelicShopService;
import rpg.relic.service.RelicGenerationService;
import rpg.relic.service.RelicIdentityService;
import rpg.relic.service.RelicKeys;
import rpg.relic.service.RelicUpgradeService;
import rpg.status.StatusModule;

import java.util.logging.Level;

/**
 * Accessory module: six equip slots (charm/ring/necklace/wing/earring/belt) whose stat bonus
 * only applies while the matching item sits in its designated slot. Those slots are virtual -
 * they live in {@link rpg.accessory.model.PlayerAccessoryEquipmentComponent}, persisted by
 * {@link AccessoryEquipmentRepository}, and are equipped through the status GUI
 * ({@code rpg.gui.listener.StatusEquipmentSlotListener}); no slot of the player's own inventory
 * carries any special meaning. Also owns the relic system (boss-dropped rollable versions of
 * the same slots, see {@code rpg.relic}) - kept in this module rather than split out since both
 * share the same slot set and effect-apply pipeline.
 */
public final class AccessoryModule implements RpgModule {

    private final AccessoryRepository repository = new AccessoryRepository();
    private final RelicConfig relicConfig = new RelicConfig();
    private AccessoryFactory factory;
    private AccessoryIdentityService identityService;
    private AccessoryEffectService effectService;
    private AccessoryEquipmentRepository equipmentRepository;
    private RelicIdentityService relicIdentityService;
    private RelicGenerationService relicGenerationService;
    private RelicUpgradeService relicUpgradeService;
    private RelicShopService relicShopService;
    private RelicEffectService relicEffectService;
    private RelicUpgradeGuiScreen relicUpgradeGuiScreen;
    private final GuiManager relicGuiManager = new GuiManager();
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "accessory";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        StatusModule statusModule = plugin.getModuleManager().get(StatusModule.class)
                .orElseThrow(() -> new IllegalStateException("accessory module requires status module"));
        DatabaseModule databaseModule = plugin.getModuleManager().get(DatabaseModule.class)
                .orElseThrow(() -> new IllegalStateException("accessory module requires database module"));
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);

        reloadAccessories();
        reloadRelics();

        this.equipmentRepository = new AccessoryEquipmentRepository(databaseModule.getDatabaseManager());
        try {
            equipmentRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize accessory equipment schema", e);
        }
        plugin.getPlayerDataManager().registerLoader(new AccessoryEquipmentManager(equipmentRepository));

        AccessoryKeys keys = new AccessoryKeys(plugin);
        this.factory = new AccessoryFactory(keys);
        this.identityService = new AccessoryIdentityService(keys, repository);
        this.effectService = new AccessoryEffectService(statusModule.getStatusService(), identityService,
                plugin.getPlayerDataManager());

        RelicKeys relicKeys = new RelicKeys(plugin);
        this.relicIdentityService = new RelicIdentityService(relicKeys);
        RelicFactory relicFactory = new RelicFactory(relicIdentityService);
        this.relicGenerationService = new RelicGenerationService(relicConfig, relicFactory);
        this.relicUpgradeService = new RelicUpgradeService(relicConfig, relicIdentityService, relicFactory, economy);
        this.relicShopService = new RelicShopService(relicConfig, relicFactory);
        this.relicEffectService = new RelicEffectService(statusModule.getStatusService(), relicIdentityService,
                relicConfig, plugin.getPlayerDataManager());

        plugin.getServer().getPluginManager().registerEvents(
                new AccessoryEquipmentJoinListener(effectService, relicEffectService), plugin);

        this.relicUpgradeGuiScreen = new RelicUpgradeGuiScreen(relicIdentityService, relicUpgradeService, plugin.getMessageManager());
        RelicCommand relicCommand = new RelicCommand(relicUpgradeGuiScreen, relicGuiManager, plugin.getMessageManager());
        plugin.getPlayerCommandRegistry().register("relic", relicCommand,
                "手に持ったレリックの厳選(サブステータス選択アップグレード)を行います。", "relic upgrade");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadAccessories();
        reloadRelics();
    }

    private void reloadAccessories() {
        plugin.getConfigManager().register("accessories.yml");
        YamlConfiguration config = plugin.getConfigManager().get("accessories.yml").get();
        repository.load(config);
    }

    private void reloadRelics() {
        plugin.getConfigManager().register("relics.yml");
        YamlConfiguration config = plugin.getConfigManager().get("relics.yml").get();
        relicConfig.load(config);
    }

    public AccessoryRepository getRepository() {
        return repository;
    }

    public AccessoryFactory getFactory() {
        return factory;
    }

    public AccessoryIdentityService getIdentityService() {
        return identityService;
    }

    /** Used by {@code GuiModule} to wire the status screen's equip slots. */
    public AccessoryEffectService getEffectService() {
        return effectService;
    }

    /** Used by {@code GuiModule} to wire the status screen's equip slots. */
    public RelicEffectService getRelicEffectService() {
        return relicEffectService;
    }

    /** Used by {@code GuiModule} so an equip/unequip click persists immediately. */
    public AccessoryEquipmentRepository getEquipmentRepository() {
        return equipmentRepository;
    }

    public RelicIdentityService getRelicIdentityService() {
        return relicIdentityService;
    }

    /** Used by {@code rpg.api.RelicApiImpl} - the only cross-plugin entry point for orelia-world's boss-drop hook. */
    public RelicGenerationService getRelicGenerationService() {
        return relicGenerationService;
    }

    public RelicShopService getRelicShopService() {
        return relicShopService;
    }

    /** Used by {@code rpg.api.RelicApiImpl} to open the same upgrade GUI {@code /ol relic upgrade} does, from an NPC. */
    public RelicUpgradeGuiScreen getRelicUpgradeGuiScreen() {
        return relicUpgradeGuiScreen;
    }

    /** Used by {@code rpg.api.RelicApiImpl} - shares one instance with {@code /ol relic}'s own command handler. */
    public GuiManager getRelicGuiManager() {
        return relicGuiManager;
    }
}
