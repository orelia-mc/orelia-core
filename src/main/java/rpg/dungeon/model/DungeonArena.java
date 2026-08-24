package rpg.dungeon.model;

/** One physical entry point (world + coordinates) a dungeon run can be spawned at. */
public record DungeonArena(String world, double x, double y, double z) {
}
