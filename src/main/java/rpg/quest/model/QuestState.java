package rpg.quest.model;

/**
 * The quest lifecycle from SOW section 11: 未受注 → 受注中 → 達成 → 報告待ち → 完了.
 * {@link #ACHIEVED} and {@link #AWAITING_REPORT} are reached together the instant every
 * objective is satisfied - there is no separate trigger between them - the player then
 * needs to talk to the quest NPC to move to {@link #COMPLETE} and receive rewards.
 */
public enum QuestState {
    NOT_ACCEPTED,
    IN_PROGRESS,
    ACHIEVED,
    AWAITING_REPORT,
    COMPLETE
}
