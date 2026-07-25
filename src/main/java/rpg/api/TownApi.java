package rpg.api;

import org.bukkit.Location;

/**
 * Cross-plugin surface over town detection - whether a location falls inside a WorldGuard
 * region configured as a town ({@code config.yml: town-detection.town-regions}). For
 * orelia-world/orelia-extra features that need to know whether a location is "in town" (e.g.
 * quest/NPC placement, safe-zone rules) without reaching into WorldGuard themselves.
 */
public interface TownApi {

    boolean isInTown(Location location);
}
