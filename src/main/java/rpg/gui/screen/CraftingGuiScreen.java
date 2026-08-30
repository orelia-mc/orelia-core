package rpg.gui.screen;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.item.manager.ItemManager;
import rpg.item.model.CraftingIngredient;
import rpg.item.model.CraftingRecipe;
import rpg.item.repository.CraftingConfigRepository;
import rpg.item.service.CraftingService;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Crafting screen (SOW follow-up "合成"): shows every configured recipe, with a live
 * ✓/✗ ingredient checklist against the viewing player's current inventory, and crafts one
 * on click.
 */
public final class CraftingGuiScreen {

    private final CraftingConfigRepository repository;
    private final CraftingService craftingService;
    private final ItemManager itemManager;
    private final GuiConfig guiConfig;
    private final MessageManager messages;

    public CraftingGuiScreen(CraftingConfigRepository repository, CraftingService craftingService,
                              ItemManager itemManager, GuiConfig guiConfig, MessageManager messages) {
        this.repository = repository;
        this.craftingService = craftingService;
        this.itemManager = itemManager;
        this.guiConfig = guiConfig;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("crafting", "&%8合成"), 54);
        int slot = 0;
        for (CraftingRecipe recipe : repository.getAll().values()) {
            if (slot >= 54) {
                break;
            }
            int buttonSlot = slot++;
            gui.set(buttonSlot, new GuiButton(icon(player, recipe), (clicker, clickType) -> {
                boolean crafted = craftingService.craft(clicker, recipe);
                messages.send(clicker, crafted ? "crafting.success" : "crafting.missing-materials", "item", recipe.getName());
                if (crafted) {
                    clicker.getOpenInventory().getTopInventory().setItem(buttonSlot, icon(clicker, recipe));
                }
            }));
        }
        return gui;
    }

    private ItemStack icon(Player player, CraftingRecipe recipe) {
        ItemStack preview = itemManager.createWeapon(recipe.getResultWeaponId()).orElse(null);
        List<Component> lore = new ArrayList<>();
        for (String line : recipe.getDescription()) {
            lore.add(ColorUtil.component(line));
        }
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        for (CraftingIngredient ingredient : recipe.getIngredients()) {
            Optional<Material> material = materialOf(ingredient);
            boolean has = material.map(m -> player.getInventory().containsAtLeast(new ItemStack(m), ingredient.getAmount())).orElse(false);
            // Material's own name shown via a translatable component - resolved client-side to
            // the viewing player's own game language, same as ShopGuiScreen's VANILLA icons.
            // Falls back to the raw configured id only if it isn't a valid Material at all
            // (misconfigured crafting.yml entry) - a rare case worth surfacing loudly rather
            // than silently prettifying into something that looks intentional.
            Component ingredientLabel = material.<Component>map(m -> Component.translatable(m.translationKey()))
                    .orElseGet(() -> Component.text(ingredient.getMaterialId()));
            lore.add(ColorUtil.component((has ? "&%a✓ " : "&%c✗ ") + ingredient.getAmount() + "x ").append(ingredientLabel));
        }
        ItemBuilder builder = new ItemBuilder(preview != null ? preview.getType() : Material.BARRIER);
        return builder.name(Component.text(recipe.getName())).loreComponents(lore).build();
    }

    private Optional<Material> materialOf(CraftingIngredient ingredient) {
        try {
            return Optional.of(Material.valueOf(ingredient.getMaterialId().trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
