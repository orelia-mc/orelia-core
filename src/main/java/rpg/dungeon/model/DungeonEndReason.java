package rpg.dungeon.model;

/** Why a {@link DungeonInstance} ended, driving both the resulting {@link DungeonInstanceStatus} and whether rewards are granted. */
public enum DungeonEndReason {
    /** Every configured enemy (and boss, if any) was killed within the time limit. Rewards are granted, quest CLEAR_DUNGEON progress is updated. */
    CLEARED,
    /** The time limit expired before the dungeon was cleared. No rewards. */
    TIMED_OUT,
    /** A party member manually left via {@code /ol dungeon retire}. No rewards. */
    RETIRED
}
