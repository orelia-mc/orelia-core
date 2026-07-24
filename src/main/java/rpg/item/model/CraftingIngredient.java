package rpg.item.model;

/**
 * One ingredient line of a {@link CraftingRecipe} - a raw vanilla {@code Material} name
 * (there's no custom crafting-material item type, only weapons are data-driven) and how many
 * of it the recipe consumes.
 */
public final class CraftingIngredient {

    private final String materialId;
    private final int amount;

    public CraftingIngredient(String materialId, int amount) {
        this.materialId = materialId;
        this.amount = amount;
    }

    public String getMaterialId() {
        return materialId;
    }

    public int getAmount() {
        return amount;
    }
}
