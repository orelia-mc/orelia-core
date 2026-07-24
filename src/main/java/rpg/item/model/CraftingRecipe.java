package rpg.item.model;

import java.util.List;

/**
 * A crafting recipe (SOW follow-up "合成"): consumes {@link #getIngredients()} to produce one
 * of {@link #getResultWeaponId()}. v1 scope is weapon-only output - weapons are the only
 * data-driven item type in {@code items.yml} today, there's no generic material/item registry
 * a recipe could target instead.
 */
public final class CraftingRecipe {

    private final String id;
    private final String name;
    private final String resultWeaponId;
    private final List<CraftingIngredient> ingredients;
    private final List<String> description;

    public CraftingRecipe(String id, String name, String resultWeaponId,
                           List<CraftingIngredient> ingredients, List<String> description) {
        this.id = id;
        this.name = name;
        this.resultWeaponId = resultWeaponId;
        this.ingredients = ingredients;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getResultWeaponId() {
        return resultWeaponId;
    }

    public List<CraftingIngredient> getIngredients() {
        return ingredients;
    }

    public List<String> getDescription() {
        return description;
    }
}
