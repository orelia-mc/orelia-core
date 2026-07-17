package rpg.item;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.item.command.ItemCommand;
import rpg.item.listener.WeaponUseListener;
import rpg.item.manager.ItemManager;
import rpg.item.repository.WeaponRepository;
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
    private ItemManager itemManager;
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

        WeaponKeys keys = new WeaponKeys(plugin);
        WeaponFactory factory = new WeaponFactory(keys);
        WeaponIdentityService identityService = new WeaponIdentityService(keys, repository);
        WeaponRequirementService requirementService = new WeaponRequirementService(jobModule.getJobService(), statusModule.getStatusService());
        this.itemManager = new ItemManager(repository, factory, identityService, requirementService);

        plugin.getServer().getPluginManager().registerEvents(
                new WeaponUseListener(identityService, requirementService, plugin.getMessageManager()), plugin);
        plugin.getPlayerCommandRegistry().register("item", new ItemCommand(itemManager, plugin.getMessageManager()),
                "武器の付与など、アイテム関連の操作を行います。", "item give <player> <id> [amount]");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadWeapons();
    }

    private void reloadWeapons() {
        plugin.getConfigManager().register("items.yml");
        YamlConfiguration config = plugin.getConfigManager().get("items.yml").get();
        repository.load(config);
    }

    public ItemManager getItemManager() {
        return itemManager;
    }
}
