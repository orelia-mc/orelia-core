package rpg.boss;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import rpg.boss.listener.BossEncounterListener;
import rpg.boss.listener.BossEnrageListener;
import rpg.boss.listener.BossFireballHitListener;
import rpg.boss.manager.BossStateManager;
import rpg.boss.repository.BossRepository;
import rpg.boss.service.BossAbilityCastService;
import rpg.boss.service.BossBarService;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.monster.MonsterModule;

import java.util.Optional;

/**
 * Boss module: config-driven boss definitions (bosses.yml) layered on top of an existing
 * monster entry, adding HP-threshold phases, an enrage damage multiplier, and periodic
 * ability casting (SOW follow-up: "スキルを発動するボス").
 */
public final class BossModule implements RpgModule {

    private static final long ABILITY_TICK_PERIOD_TICKS = 20L;
    private static final long BOSS_BAR_TICK_PERIOD_TICKS = 10L;

    private final BossRepository repository = new BossRepository();
    private final BossStateManager stateManager = new BossStateManager();
    private MonsterModule monsterModule;
    private BossAbilityCastService abilityCastService;
    private BossBarService bossBarService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "boss";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        this.monsterModule = plugin.getModuleManager().get(MonsterModule.class)
                .orElseThrow(() -> new IllegalStateException("boss module requires monster module"));

        reloadBosses();

        this.abilityCastService = new BossAbilityCastService(plugin, monsterModule.getSpawnService(), repository);
        this.bossBarService = new BossBarService(monsterModule.getSpawnService());

        plugin.getServer().getPluginManager().registerEvents(
                new BossEncounterListener(monsterModule.getSpawnService(), repository, stateManager, abilityCastService,
                        plugin.getMessageManager()), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new BossEnrageListener(monsterModule.getSpawnService(), repository, stateManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new BossFireballHitListener(plugin), plugin);

        plugin.getSchedulerService().runTimer(abilityCastService::tick, ABILITY_TICK_PERIOD_TICKS, ABILITY_TICK_PERIOD_TICKS);
        plugin.getSchedulerService().runTimer(bossBarService::tick, BOSS_BAR_TICK_PERIOD_TICKS, BOSS_BAR_TICK_PERIOD_TICKS);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadBosses();
    }

    private void reloadBosses() {
        plugin.getConfigManager().register("bosses.yml");
        YamlConfiguration config = plugin.getConfigManager().get("bosses.yml").get();
        repository.load(config);
    }

    public Optional<LivingEntity> spawn(String bossId, Location location) {
        return spawn(bossId, location, null);
    }

    /** {@code targetLevel} scales the underlying monster's hp/attack-power/defense - see {@link rpg.monster.service.MonsterSpawnService#spawn(String, Location, java.util.UUID, Integer)}. */
    public Optional<LivingEntity> spawn(String bossId, Location location, Integer targetLevel) {
        Optional<LivingEntity> entity = repository.findById(bossId)
                .flatMap(boss -> monsterModule.getSpawnService().spawn(boss.getMonsterId(), location, null, targetLevel));
        entity.ifPresent(e -> {
            abilityCastService.register(e);
            monsterModule.getSpawnService().dataOf(e).ifPresent(data -> bossBarService.register(e, data));
        });
        return entity;
    }

    public BossRepository getRepository() {
        return repository;
    }
}
