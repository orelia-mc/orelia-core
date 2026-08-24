package rpg.world.cutscene;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.api.EffectApi;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.world.cutscene.repository.CutSceneRepository;
import rpg.world.cutscene.service.CutScenePlaybackService;

/**
 * CutScene module: scripted camera/message/effect/title sequences (cutscenes.yml).
 */
public final class CutSceneModule implements RpgModule {

    private final CutSceneRepository repository = new CutSceneRepository();
    private CutScenePlaybackService playbackService;
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "cutscene";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        EffectApi effectApi = plugin.getServer().getServicesManager().load(EffectApi.class);

        reloadCutScenes();

        this.playbackService = new CutScenePlaybackService(repository, plugin.getSchedulerService(), effectApi);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadCutScenes();
    }

    private void reloadCutScenes() {
        plugin.getConfigManager().register("cutscenes.yml");
        YamlConfiguration config = plugin.getConfigManager().get("cutscenes.yml").get();
        repository.load(config);
    }

    public CutScenePlaybackService getPlaybackService() {
        return playbackService;
    }
}
