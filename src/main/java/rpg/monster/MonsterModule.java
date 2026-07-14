package rpg.monster;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.DatabaseModule;
import rpg.economy.EconomyModule;
import rpg.item.ItemModule;
import rpg.monster.listener.MonsterCombatListener;
import rpg.monster.listener.MonsterDeathListener;
import rpg.monster.listener.VanillaHostileSpawnBlockerListener;
import rpg.monster.repository.MonsterRepository;
import rpg.monster.service.MonsterDropService;
import rpg.monster.service.MonsterKeys;
import rpg.monster.service.MonsterSpawnService;
import rpg.monster.spawnpoint.manager.MonsterSpawnPointManager;
import rpg.monster.spawnpoint.repository.MonsterSpawnPointRepository;
import rpg.monster.spawnpoint.service.MonsterSpawnPointService;
import rpg.status.StatusModule;

import java.util.logging.Level;

/**
 * Monster module: config-driven monster templates (monsters.yml), spawning, combat stat
 * application, drop/EXP/money rewards on death, and admin-placed recurring spawn points.
 */
public final class MonsterModule implements RpgModule {

    private static final long SPAWN_POINT_TICK_PERIOD_TICKS = 20L;

    private final MonsterRepository repository = new MonsterRepository();
    private MonsterSpawnService spawnService;
    private MonsterSpawnPointService spawnPointService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "monster";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        ItemModule itemModule = plugin.getModuleManager().get(ItemModule.class)
                .orElseThrow(() -> new IllegalStateException("monster module requires item module"));
        EconomyModule economyModule = plugin.getModuleManager().get(EconomyModule.class)
                .orElseThrow(() -> new IllegalStateException("monster module requires economy module"));
        StatusModule statusModule = plugin.getModuleManager().get(StatusModule.class)
                .orElseThrow(() -> new IllegalStateException("monster module requires status module"));
        DatabaseModule databaseModule = plugin.getModuleManager().get(DatabaseModule.class)
                .orElseThrow(() -> new IllegalStateException("monster module requires database module"));

        reloadMonsters();

        MonsterKeys keys = new MonsterKeys(plugin);
        this.spawnService = new MonsterSpawnService(keys, repository);
        MonsterDropService dropService = new MonsterDropService(
                itemModule.getItemManager(), economyModule.getEconomyService(), statusModule.getStatusService());

        MonsterSpawnPointRepository spawnPointRepository = new MonsterSpawnPointRepository(databaseModule.getDatabaseManager());
        try {
            spawnPointRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize monster spawn point schema", e);
        }
        this.spawnPointService = new MonsterSpawnPointService(spawnPointRepository, new MonsterSpawnPointManager(), spawnService, repository);
        spawnPointService.loadAll();

        plugin.getServer().getPluginManager().registerEvents(
                new MonsterCombatListener(spawnService, itemModule.getItemManager().getIdentityService()), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MonsterDeathListener(spawnService, dropService, spawnPointService), plugin);

        boolean disableVanillaSpawning = plugin.getConfigManager().get("config.yml").get()
                .getBoolean("monster.disable-vanilla-hostile-spawning", true);
        if (disableVanillaSpawning) {
            plugin.getServer().getPluginManager().registerEvents(new VanillaHostileSpawnBlockerListener(), plugin);
        }

        plugin.getSchedulerService().runTimer(spawnPointService::tick, SPAWN_POINT_TICK_PERIOD_TICKS, SPAWN_POINT_TICK_PERIOD_TICKS);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadMonsters();
    }

    private void reloadMonsters() {
        plugin.getConfigManager().register("monsters.yml");
        YamlConfiguration config = plugin.getConfigManager().get("monsters.yml").get();
        repository.load(config);
    }

    public MonsterRepository getRepository() {
        return repository;
    }

    public MonsterSpawnService getSpawnService() {
        return spawnService;
    }

    public MonsterSpawnPointService getSpawnPointService() {
        return spawnPointService;
    }
}
