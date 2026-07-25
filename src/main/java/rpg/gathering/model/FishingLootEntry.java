package rpg.gathering.model;

import org.bukkit.Material;

/** One weighted entry in a town's {@code fishing.yml} catchable-item table. */
public record FishingLootEntry(Material item, int weight, int minAmount, int maxAmount) {
}
