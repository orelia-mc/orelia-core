package rpg.item.service;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.item.manager.ItemManager;
import rpg.item.model.CraftingIngredient;
import rpg.item.model.CraftingRecipe;

import java.util.Optional;

/**
 * Checks a player's inventory against a {@link CraftingRecipe}'s ingredients and, on success,
 * consumes them and gives the crafted weapon (SOW follow-up "合成"). Fails closed on a
 * misconfigured ingredient/result id rather than partially consuming materials.
 */
public final class CraftingService {

    private final ItemManager itemManager;

    public CraftingService(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    public boolean canCraft(Player player, CraftingRecipe recipe) {
        for (CraftingIngredient ingredient : recipe.getIngredients()) {
            Material material = materialOf(ingredient).orElse(null);
            if (material == null || !player.getInventory().containsAtLeast(new ItemStack(material), ingredient.getAmount())) {
                return false;
            }
        }
        return true;
    }

    /** Consumes the recipe's ingredients and gives the crafted weapon; drops it on the ground if the inventory is full. Does nothing and returns {@code false} if materials are short or the result weapon id doesn't resolve. */
    public boolean craft(Player player, CraftingRecipe recipe) {
        if (!canCraft(player, recipe)) {
            return false;
        }
        Optional<ItemStack> result = itemManager.createWeapon(recipe.getResultWeaponId());
        if (result.isEmpty()) {
            return false;
        }
        for (CraftingIngredient ingredient : recipe.getIngredients()) {
            Material material = materialOf(ingredient).orElseThrow();
            player.getInventory().removeItem(new ItemStack(material, ingredient.getAmount()));
        }
        player.getInventory().addItem(result.get()).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        return true;
    }

    private Optional<Material> materialOf(CraftingIngredient ingredient) {
        try {
            return Optional.of(Material.valueOf(ingredient.getMaterialId().trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
