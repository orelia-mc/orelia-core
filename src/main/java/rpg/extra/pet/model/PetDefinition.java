package rpg.extra.pet.model;

import org.bukkit.entity.EntityType;

/**
 * Static pet definition loaded from {@code pets.yml} (SOW PetModule).
 */
public final class PetDefinition {

    private final String id;
    private final String name;
    private final EntityType entityType;
    private final double price;

    public PetDefinition(String id, String name, EntityType entityType, double price) {
        this.id = id;
        this.name = name;
        this.entityType = entityType;
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

    public double getPrice() {
        return price;
    }
}
