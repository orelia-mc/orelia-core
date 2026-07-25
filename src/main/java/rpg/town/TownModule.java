package rpg.town;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.region.RegionModule;
import rpg.town.config.TownConfig;
import rpg.town.service.TownDetectionService;

/**
 * Town detection module: whether a {@link org.bukkit.Location} is "inside a town", based on
 * WorldGuard regions listed in {@code config.yml: town-detection.town-regions}. Used to
 * suppress monster spawning inside towns ({@link rpg.monster.service.MonsterSpawnService}) and
 * published to orelia-world/orelia-extra via {@code rpg.api.TownApi}.
 */
public final class TownModule implements RpgModule {

    private final TownConfig config = new TownConfig();
    private TownDetectionService detectionService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "town";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        RegionModule regionModule = plugin.getModuleManager().get(RegionModule.class)
                .orElseThrow(() -> new IllegalStateException("town module requires region module"));

        reloadConfig();
        this.detectionService = new TownDetectionService(regionModule.getQueryService(), config);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadConfig();
    }

    private void reloadConfig() {
        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        this.config.load(config);
    }

    public TownDetectionService getDetectionService() {
        return detectionService;
    }
}
