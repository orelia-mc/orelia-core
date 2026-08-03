package rpg.extra.mount.model;

import org.bukkit.entity.EntityType;

/**
 * Static mount definition loaded from {@code mounts.yml} (SOW MountModule).
 */
public final class MountDefinition {

    private final String id;
    private final String name;
    private final EntityType entityType;
    private final double speed;
    private final double price;

    public MountDefinition(String id, String name, EntityType entityType, double speed, double price) {
        this.id = id;
        this.name = name;
        this.entityType = entityType;
        this.speed = speed;
        this.price = price;
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
}
