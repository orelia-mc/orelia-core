package rpg.world.cutscene.service;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import rpg.api.EffectApi;
import rpg.core.scheduler.SchedulerService;
import rpg.util.ColorUtil;
import rpg.world.cutscene.model.CutSceneData;
import rpg.world.cutscene.model.CutSceneStep;
import rpg.world.cutscene.repository.CutSceneRepository;

import java.time.Duration;

/**
 * Plays a {@link CutSceneData} for one player by scheduling each step at its configured
 * delay (SOW CutSceneModule). Camera control is a simplified teleport+look rather than a
 * true detached spectator camera, which would need client-side packet tricks outside this
 * scaffold's scope.
 */
public final class CutScenePlaybackService {

    private final CutSceneRepository repository;
    private final SchedulerService schedulerService;
    private final EffectApi effectApi;

    public CutScenePlaybackService(CutSceneRepository repository, SchedulerService schedulerService, EffectApi effectApi) {
        this.repository = repository;
        this.schedulerService = schedulerService;
        this.effectApi = effectApi;
    }

    public boolean play(Player player, String cutsceneId) {
        CutSceneData data = repository.findById(cutsceneId).orElse(null);
        if (data == null) {
            return false;
        }
        for (CutSceneStep step : data.getSteps()) {
            schedulerService.runLater(() -> runStep(player, step), step.getDelayTicks());
        }
        return true;
    }

    private void runStep(Player player, CutSceneStep step) {
        if (!player.isOnline()) {
            return;
        }
        switch (step.getType()) {
            case MESSAGE -> {
                if (step.getMessage() != null) {
                    player.sendMessage(ColorUtil.colorize(step.getMessage()));
                }
            }
            case TITLE -> player.showTitle(Title.title(
                    net.kyori.adventure.text.Component.text(ColorUtil.colorize(step.getTitle() == null ? "" : step.getTitle())),
                    net.kyori.adventure.text.Component.text(ColorUtil.colorize(step.getSubtitle() == null ? "" : step.getSubtitle())),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))));
            case EFFECT -> {
                if (step.getEffectId() != null && effectApi != null) {
                    effectApi.playOnEntity(player, step.getEffectId());
                }
            }
            case CAMERA -> {
                if (step.getCameraWorld() != null) {
                    var world = Bukkit.getWorld(step.getCameraWorld());
                    if (world != null) {
                        player.teleport(new Location(world, step.getCameraX(), step.getCameraY(), step.getCameraZ(),
                                step.getCameraYaw(), step.getCameraPitch()));
                    }
                }
            }
        }
    }
}
