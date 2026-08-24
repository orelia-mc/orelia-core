package rpg.quest.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's live progress on one accepted quest: current state and per-objective
 * counters (indexed by position in {@link QuestData#getObjectives()}).
 */
public final class PlayerQuestProgress {

    private QuestState state;
    private final Map<Integer, Integer> objectiveProgress = new HashMap<>();

    public PlayerQuestProgress(QuestState state) {
        this.state = state;
    }

    public QuestState getState() {
        return state;
    }

    public void setState(QuestState state) {
        this.state = state;
    }

    public int getProgress(int objectiveIndex) {
        return objectiveProgress.getOrDefault(objectiveIndex, 0);
    }

    public void setProgress(int objectiveIndex, int amount) {
        objectiveProgress.put(objectiveIndex, amount);
    }

    public Map<Integer, Integer> getAllProgress() {
        return Map.copyOf(objectiveProgress);
    }
}
