package rpg.item;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.item.command.ItemCommand;
import rpg.item.config.WeaponLevelConfig;
import rpg.item.manager.ItemManager;
import rpg.item.repository.CraftingConfigRepository;
import rpg.item.repository.WeaponRepository;
import rpg.item.service.CraftingService;
import rpg.item.service.WeaponFactory;
import rpg.item.service.WeaponIdentityService;
import rpg.item.service.WeaponKeys;
import rpg.item.service.WeaponRequirementService;
import rpg.job.JobModule;
import rpg.status.StatusModule;

/**
 * Weapon module: config-driven weapon templates (items.yml), ItemStack generation,
 * identity resolution, and requirement enforcement on hit.
 */
public final class ItemModule implements RpgModule {

    private final WeaponRepository repository = new WeaponRepository();
    private final WeaponLevelConfig levelConfig = new WeaponLevelConfig();
    private final CraftingConfigRepository craftingRepository = new CraftingConfigRepository();
    private ItemManager itemManager;
    private CraftingService craftingService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "item";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        JobModule jobModule = plugin.getModuleManager().get(JobModule.class)
                .orElseThrow(() -> new IllegalStateException("item module requires job module"));
        StatusModule statusModule = plugin.getModuleManager().get(StatusModule.class)
                .orElseThrow(() -> new IllegalStateException("item module requires status module"));

        reloadWeapons();
        loadLevelConfig();
        reloadCrafting();

        WeaponKeys keys = new WeaponKeys(plugin);
        WeaponFactory factory = new WeaponFactory(keys);
        WeaponIdentityService identityService = new WeaponIdentityService(keys, repository, levelConfig);
        WeaponRequirementService requirementService = new WeaponRequirementService(jobModule.getJobService(), statusModule.getStatusService());
        this.itemManager = new ItemManager(repository, factory, identityService, requirementService);
        this.craftingService = new CraftingService(itemManager);

        // Damage-computation logic (weapon hit -> ATK% -> DEF -> crit -> weakness) lives in
        // rpg.monster.listener.CombatDamageListener, registered by MonsterModule (which is
        // the only module positioned after both this one and StatusModule in the enable
        // order, so it can pull in WeaponIdentityService/WeaponRequirementService/StatusService).
        plugin.getAdminCommandRegistry().register("item",
                new ItemCommand(itemManager, statusModule.getStatusService(), plugin.getMessageManager()),
                "武器の付与・武器レベルアップなど、アイテム関連の操作を行います。", "item give <player> <id> [amount] | item levelup");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadWeapons();
        loadLevelConfig();
        reloadCrafting();
    }

    private void reloadWeapons() {
        plugin.getConfigManager().register("items.yml");
        YamlConfiguration config = plugin.getConfigManager().get("items.yml").get();
        repository.load(config);
    }

    private void loadLevelConfig() {
        levelConfig.load(plugin.getConfigManager().get("config.yml").get());
    }

    private void reloadCrafting() {
        plugin.getConfigManager().register("crafting.yml");
        YamlConfiguration config = plugin.getConfigManager().get("crafting.yml").get();
        craftingRepository.load(config);
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public CraftingConfigRepository getCraftingRepository() {
        return craftingRepository;
    }

    public CraftingService getCraftingService() {
        return craftingService;
    }
}
