package rpg.extra.duel.model;

/** One physical location a duel can be spawned at (mirrors rpg.dungeon.model.DungeonArena, but flat - no parent dungeon-id, since a duel isn't tied to any other content entity). */
public record DuelArena(String world, double x, double y, double z, float yaw, float pitch) {
}
