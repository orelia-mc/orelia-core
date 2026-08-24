package rpg.world.cutscene.model;

import java.util.List;

public final class CutSceneData {

    private final String id;
    private final List<CutSceneStep> steps;

    public CutSceneData(String id, List<CutSceneStep> steps) {
        this.id = id;
        this.steps = steps;
    }

    public String getId() {
        return id;
    }

    public List<CutSceneStep> getSteps() {
        return steps;
    }
}
