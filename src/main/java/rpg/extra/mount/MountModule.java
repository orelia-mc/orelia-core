package rpg.extra.mount;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.mount.command.MountCommand;
import rpg.extra.mount.listener.MountLifecycleListener;
import rpg.extra.mount.manager.MountManager;
import rpg.extra.mount.repository.MountConfigRepository;
import rpg.extra.mount.repository.MountOwnershipRepository;
import rpg.extra.mount.service.MountService;

import java.util.logging.Level;

/**
 * Mount module: config-driven rideable mounts the player unlocks and summons/dismisses (SOW
 * MountModule). Money settles through Vault's {@link Economy}.
 */
public final class MountModule implements RpgModule {

    private final MountConfigRepository configRepository = new MountConfigRepository();
    private final MountManager mountManager = new MountManager();
    private MountService mountService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "mount";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("mount module requires OreliaCore's DatabaseManager");
        }
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);
        if (economy == null) {
            throw new IllegalStateException("mount module requires Vault's Economy service");
        }

        reloadMounts();

        MountOwnershipRepository ownershipRepository = new MountOwnershipRepository(databaseManager);
        try {
            ownershipRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize mount schema", e);
        }

        this.mountService = new MountService(configRepository, ownershipRepository, mountManager, economy);
        mountService.loadAll();

        plugin.getServer().getPluginManager().registerEvents(new MountLifecycleListener(mountManager), plugin);
        plugin.getPlayerCommandRegistry().register("mount", new MountCommand(mountService, plugin.getMessageManager()),
                "乗り物を管理します。", "mount [list|buy <id>|summon [id]|dismiss]");
    }

    @Override
    public void onDisable() {
        mountManager.despawnAll();
    }

    @Override
    public void onReload() {
        reloadMounts();
    }

    private void reloadMounts() {
        plugin.getConfigManager().register("mounts.yml");
        YamlConfiguration config = plugin.getConfigManager().get("mounts.yml").get();
        configRepository.load(config);
    }

    public MountService getMountService() {
        return mountService;
    }
}
