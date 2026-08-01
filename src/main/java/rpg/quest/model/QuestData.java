package rpg.quest.model;

import java.util.List;

/**
 * Static quest definition loaded from {@code quests.yml}, including the SOW section 11
 * "クエストフラグ" unlock/availability rules.
 */
public final class QuestData {

    private final String id;
    private final String name;
    private final QuestType type;
    private final List<String> description;
    private final List<QuestObjective> objectives;
    private final QuestReward reward;
    private final boolean repeatable;
    private final boolean partyOnly;
    private final int requiredLevel;
    private final List<String> prerequisiteQuestIds;
    /** -1 disables the time-of-day gate ("時間帯で発生クエスト変更"). */
    private final int availableHourStart;
    private final int availableHourEnd;
    /** Only meaningful when {@link #repeatable} is true; 0 (the default) means no cooldown - instantly re-acceptable. */
    private final double cooldownHours;

    public QuestData(String id, String name, QuestType type, List<String> description, List<QuestObjective> objectives,
                      QuestReward reward, boolean repeatable, boolean partyOnly, int requiredLevel,
                      List<String> prerequisiteQuestIds, int availableHourStart, int availableHourEnd, double cooldownHours) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.objectives = objectives;
        this.reward = reward;
        this.repeatable = repeatable;
        this.partyOnly = partyOnly;
        this.requiredLevel = requiredLevel;
        this.prerequisiteQuestIds = prerequisiteQuestIds;
        this.availableHourStart = availableHourStart;
        this.availableHourEnd = availableHourEnd;
        this.cooldownHours = cooldownHours;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public QuestType getType() {
        return type;
    }

    public List<String> getDescription() {
        return description;
    }

    public List<QuestObjective> getObjectives() {
        return objectives;
    }

    public QuestReward getReward() {
        return reward;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public boolean isPartyOnly() {
        return partyOnly;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public List<String> getPrerequisiteQuestIds() {
        return prerequisiteQuestIds;
    }

    public double getCooldownHours() {
        return cooldownHours;
    }

    public boolean isAvailableAtHour(int hour) {
        if (availableHourStart < 0 || availableHourEnd < 0) {
            return true;
        }
        if (availableHourStart <= availableHourEnd) {
            return hour >= availableHourStart && hour < availableHourEnd;
        }
        return hour >= availableHourStart || hour < availableHourEnd;
    }
}
