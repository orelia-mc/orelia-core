package rpg.item.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.item.model.CraftingIngredient;
import rpg.item.model.CraftingRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link CraftingRecipe}, rebuilt from {@code crafting.yml} on
 * load and on {@code /oladmin reload} - same "add via config only" contract as
 * {@link WeaponRepository}.
 */
public final class CraftingConfigRepository {

    private Map<String, CraftingRecipe> recipes = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, CraftingRecipe> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("recipes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection recipeSection = section.getConfigurationSection(id);
                if (recipeSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, recipeSection));
            }
        }
        this.recipes = loaded;
    }

    private CraftingRecipe parse(String id, ConfigurationSection section) {
        List<CraftingIngredient> ingredients = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("ingredients")) {
            Object material = raw.get("material");
            Object amount = raw.get("amount");
            if (material != null && amount != null) {
                ingredients.add(new CraftingIngredient(material.toString(), Integer.parseInt(amount.toString())));
            }
        }
        return new CraftingRecipe(
                id,
                section.getString("name", id),
                section.getString("result-weapon-id", ""),
                ingredients,
                section.getStringList("description"));
    }

    public Optional<CraftingRecipe> findById(String id) {
        return Optional.ofNullable(recipes.get(id));
    }

    public Map<String, CraftingRecipe> getAll() {
        return Map.copyOf(recipes);
    }
}
