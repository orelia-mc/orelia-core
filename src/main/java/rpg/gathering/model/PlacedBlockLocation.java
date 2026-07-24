package rpg.gathering.model;

/** Coordinate key for a block a player placed by hand (SOW: 木こり自動再生成の除外). */
public record PlacedBlockLocation(String world, int x, int y, int z) {
}
