package rpg.skill;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.DatabaseModule;
import rpg.item.ItemModule;
import rpg.skill.executor.ArrowVolleyExecutor;
import rpg.skill.executor.DashStrikeExecutor;
import rpg.skill.executor.ExplosiveArrowExecutor;
import rpg.skill.executor.MeleeAoeExecutor;
import rpg.skill.executor.MeleeConeExecutor;
import rpg.skill.executor.SkillDamage;
import rpg.skill.listener.ArrowSkillDamageListener;
import rpg.skill.listener.ExplosiveArrowHitListener;
import rpg.skill.listener.SkillActivationListener;
import rpg.skill.manager.SkillExecutorRegistry;
import rpg.skill.manager.SkillManager;
import rpg.skill.repository.PlayerSkillRepository;
import rpg.skill.repository.SkillRepository;
import rpg.skill.service.SkillCastService;
import rpg.skill.service.SkillProgressService;
import rpg.skill.service.SkillSocketService;
import rpg.status.StatusModule;

import java.util.logging.Level;

/**
 * Weapon skill module: config-driven skill definitions (skills.yml), executor archetypes,
 * skill sockets on weapon ItemStacks, and skill point progression.
 */
public final class SkillModule implements RpgModule {

    private final SkillRepository skillRepository = new SkillRepository();
    private SkillCastService castService;
    private SkillProgressService progressService;
    private SkillSocketService socketService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "skill";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseModule databaseModule = plugin.getModuleManager().get(DatabaseModule.class)
                .orElseThrow(() -> new IllegalStateException("skill module requires database module"));
        ItemModule itemModule = plugin.getModuleManager().get(ItemModule.class)
                .orElseThrow(() -> new IllegalStateException("skill module requires item module"));
        StatusModule statusModule = plugin.getModuleManager().get(StatusModule.class)
                .orElseThrow(() -> new IllegalStateException("skill module requires status module"));

        reloadSkills();

        PlayerSkillRepository repository = new PlayerSkillRepository(databaseModule.getDatabaseManager());
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize skill schema", e);
        }
        SkillManager skillManager = new SkillManager(repository);
        plugin.getPlayerDataManager().registerLoader(skillManager);

        this.socketService = new SkillSocketService(plugin);
        this.progressService = new SkillProgressService(plugin.getPlayerDataManager(), skillRepository);

        SkillDamage skillDamage = new SkillDamage(plugin, itemModule.getItemManager().getIdentityService());
        SkillExecutorRegistry executorRegistry = new SkillExecutorRegistry();
        executorRegistry.register("MELEE_CONE", new MeleeConeExecutor(skillDamage));
        executorRegistry.register("MELEE_AOE", new MeleeAoeExecutor(skillDamage));
        executorRegistry.register("DASH_STRIKE", new DashStrikeExecutor(skillDamage));
        executorRegistry.register("ARROW_VOLLEY", new ArrowVolleyExecutor(plugin));
        executorRegistry.register("EXPLOSIVE_ARROW", new ExplosiveArrowExecutor(plugin));

        this.castService = new SkillCastService(plugin.getPlayerDataManager(), skillRepository, executorRegistry,
                socketService, itemModule.getItemManager().getIdentityService(), statusModule.getStatusService());

        plugin.getServer().getPluginManager().registerEvents(
                new SkillActivationListener(castService, socketService, itemModule.getItemManager().getIdentityService(),
                        plugin.getMessageManager()), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ArrowSkillDamageListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ExplosiveArrowHitListener(), plugin);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadSkills();
    }

    private void reloadSkills() {
        plugin.getConfigManager().register("skills.yml");
        YamlConfiguration config = plugin.getConfigManager().get("skills.yml").get();
        skillRepository.load(config);
    }

    public SkillRepository getSkillRepository() {
        return skillRepository;
    }

    public SkillCastService getCastService() {
        return castService;
    }

    public SkillProgressService getProgressService() {
        return progressService;
    }

    public SkillSocketService getSocketService() {
        return socketService;
    }
}
