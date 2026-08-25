package rpg.extra.pet.model;

import org.bukkit.entity.EntityType;

import java.util.Map;

/**
 * Static pet definition loaded from {@code pets.yml} (SOW PetModule).
 */
public final class PetDefinition {

    private final String id;
    private final String name;
    private final EntityType entityType;
    private final double price;
    private final PetGrowthTemplate growth;

    public PetDefinition(String id, String name, EntityType entityType, double price, PetGrowthTemplate growth) {
        this.id = id;
        this.name = name;
        this.entityType = entityType;
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

    public double getPrice() {
        return price;
    }

    /** Never null - a species with no {@code growth:} section gets {@link PetGrowthTemplate#none()} (opt-out, no bonus at any level). */
    public PetGrowthTemplate getGrowth() {
        return growth;
    }

    /**
     * Per-species growth curve: {@code maxLevel} caps how far {@code expPerLevel}-based leveling
     * goes, {@code perLevelStats} is the stat-name-to-per-level-amount map applied (multiplied by
     * the pet's current level) as a {@code StatusApi#setEquipmentContribution} while summoned.
     * Absent {@code growth:} in {@code pets.yml} means opt-out - {@link #none()} caps at level 1
     * with no stats, so such a species can never actually grant a bonus.
     */
    public record PetGrowthTemplate(int maxLevel, long expPerLevel, Map<String, Double> perLevelStats) {

        public static PetGrowthTemplate none() {
            return new PetGrowthTemplate(1, 0, Map.of());
        }
    }
}
