package rpg.world.cutscene.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.world.cutscene.model.CutSceneData;
import rpg.world.cutscene.model.CutSceneStep;
import rpg.world.cutscene.model.CutSceneStepType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link CutSceneData}, rebuilt from {@code cutscenes.yml}.
 */
public final class CutSceneRepository {

    private Map<String, CutSceneData> cutscenes = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, CutSceneData> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("cutscenes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection cutsceneSection = section.getConfigurationSection(id);
                if (cutsceneSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, cutsceneSection));
            }
        }
        this.cutscenes = loaded;
    }

    private CutSceneData parse(String id, ConfigurationSection section) {
        List<CutSceneStep> steps = new ArrayList<>();
        ConfigurationSection stepsSection = section.getConfigurationSection("steps");
        if (stepsSection != null) {
            for (String stepId : stepsSection.getKeys(false)) {
                ConfigurationSection step = stepsSection.getConfigurationSection(stepId);
                if (step == null) {
                    continue;
                }
                ConfigurationSection camera = step.getConfigurationSection("camera");
                steps.add(new CutSceneStep(
                        CutSceneStepType.valueOf(step.getString("type", "MESSAGE").trim().toUpperCase()),
                        step.getLong("delay-ticks", 0),
                        step.getString("message"),
                        step.getString("title"),
                        step.getString("subtitle"),
                        step.getString("effect-id"),
                        camera != null ? camera.getString("world", "world") : null,
                        camera != null ? camera.getDouble("x", 0) : 0,
                        camera != null ? camera.getDouble("y", 64) : 0,
                        camera != null ? camera.getDouble("z", 0) : 0,
                        camera != null ? (float) camera.getDouble("yaw", 0) : 0,
                        camera != null ? (float) camera.getDouble("pitch", 0) : 0));
            }
        }
        return new CutSceneData(id, steps);
    }

    public Optional<CutSceneData> findById(String id) {
        return Optional.ofNullable(cutscenes.get(id));
    }
}
