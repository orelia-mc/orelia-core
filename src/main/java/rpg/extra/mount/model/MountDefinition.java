package rpg.extra.mount.model;

import org.bukkit.entity.EntityType;

import java.util.Map;

/**
 * Static mount definition loaded from {@code mounts.yml} (SOW MountModule).
 */
public final class MountDefinition {

    private final String id;
    private final String name;
    private final EntityType entityType;
    private final double speed;
    private final double price;
    private final MountGrowthTemplate growth;

    public MountDefinition(String id, String name, EntityType entityType, double speed, double price, MountGrowthTemplate growth) {
        this.id = id;
        this.name = name;
        this.entityType = entityType;
        this.speed = speed;
        this.price = price;
        this.growth = growth;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public double getSpeed() {
        return speed;
    }

    public double getPrice() {
        return price;
    }

    /** Never null - a species with no {@code growth:} section gets {@link MountGrowthTemplate#none()} (opt-out, no bonus at any level). */
    public MountGrowthTemplate getGrowth() {
        return growth;
    }

    /** Per-species growth curve - same shape as {@code rpg.extra.pet.model.PetDefinition.PetGrowthTemplate}. */
    public record MountGrowthTemplate(int maxLevel, long expPerLevel, Map<String, Double> perLevelStats) {

        public static MountGrowthTemplate none() {
            return new MountGrowthTemplate(1, 0, Map.of());
        }
    }
}
