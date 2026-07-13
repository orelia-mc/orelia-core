package rpg.gathering.model;

/** One level band's bulk-action radius (SOW 3.3 {@code level-ranges} entry). */
public record LevelRange(int minLevel, int maxLevel, int radius) {
}
