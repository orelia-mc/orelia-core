package rpg.dungeon.model;

import org.bukkit.Location;

/**
 * A block's identity for the dungeon-trigger registry: world name + block coordinates.
 * Blocks have no PDC equivalent to an entity's {@code PersistentDataContainer} unless
 * they're a {@code TileState}, so trigger blocks are tracked by normalized location instead.
 */
public record DungeonBlockKey(String world, int x, int y, int z) {

    public static DungeonBlockKey of(Location location) {
        return new DungeonBlockKey(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
