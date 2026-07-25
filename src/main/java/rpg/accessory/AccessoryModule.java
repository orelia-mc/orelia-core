package rpg.accessory;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.accessory.listener.AccessorySlotListener;
import rpg.accessory.repository.AccessoryRepository;
import rpg.accessory.service.AccessoryEffectService;
import rpg.accessory.service.AccessoryFactory;
import rpg.accessory.service.AccessoryIdentityService;
import rpg.accessory.service.AccessoryKeys;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.gui.framework.GuiManager;
import rpg.relic.command.RelicCommand;
import rpg.relic.config.RelicConfig;
import rpg.relic.gui.RelicUpgradeGuiScreen;
import rpg.relic.service.RelicEffectService;
import rpg.relic.service.RelicFactory;
import rpg.relic.service.RelicGenerationService;
import rpg.relic.service.RelicIdentityService;
import rpg.relic.service.RelicKeys;
import rpg.relic.service.RelicUpgradeService;
import rpg.status.StatusModule;

/**
 * Accessory module: dedicated bottom-row inventory slots (charm/ring/necklace/wing/earring/belt)
 * whose stat bonus only applies while the matching item sits in its designated slot. Also owns
 * the relic system (boss-dropped rollable versions of the same slots, see {@code rpg.relic}) -
 * kept in this module rather than split out since both share {@link AccessorySlotListener} and
 * the same slot layout.
 */
public final class AccessoryModule implements RpgModule {

    private final AccessoryRepository repository = new AccessoryRepository();
    private final RelicConfig relicConfig = new RelicConfig();
    private AccessoryFactory factory;
    private AccessoryIdentityService identityService;
    private RelicIdentityService relicIdentityService;
    private RelicGenerationService relicGenerationService;
    private RelicUpgradeService relicUpgradeService;
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
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);

        reloadAccessories();
        reloadRelics();

        AccessoryKeys keys = new AccessoryKeys(plugin);
        this.factory = new AccessoryFactory(keys);
        this.identityService = new AccessoryIdentityService(keys, repository);
        AccessoryEffectService effectService = new AccessoryEffectService(statusModule.getStatusService(), identityService);

        RelicKeys relicKeys = new RelicKeys(plugin);
        this.relicIdentityService = new RelicIdentityService(relicKeys);
        RelicFactory relicFactory = new RelicFactory(relicIdentityService);
        this.relicGenerationService = new RelicGenerationService(relicConfig, relicFactory);
        this.relicUpgradeService = new RelicUpgradeService(relicConfig, relicIdentityService, relicFactory, economy);
        RelicEffectService relicEffectService = new RelicEffectService(statusModule.getStatusService(), relicIdentityService, relicConfig);

        plugin.getServer().getPluginManager().registerEvents(
                new AccessorySlotListener(identityService, effectService, relicIdentityService, relicEffectService, plugin.getSchedulerService()), plugin);

        RelicUpgradeGuiScreen relicGuiScreen = new RelicUpgradeGuiScreen(relicIdentityService, relicUpgradeService, plugin.getMessageManager());
        RelicCommand relicCommand = new RelicCommand(relicGuiScreen, new GuiManager(), plugin.getMessageManager());
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

    public RelicIdentityService getRelicIdentityService() {
        return relicIdentityService;
    }

    /** Used by {@code rpg.api.RelicApiImpl} - the only cross-plugin entry point for orelia-world's boss-drop hook. */
    public RelicGenerationService getRelicGenerationService() {
        return relicGenerationService;
    }
}
