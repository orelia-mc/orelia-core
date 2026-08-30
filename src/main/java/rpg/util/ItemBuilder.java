package rpg.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Fluent {@link ItemStack} builder used by the item/skill/accessory/GUI modules so item
 * construction (name, lore, model data, PDC tags) is written once instead of repeated
 * per-module boilerplate.
 */
public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(String displayName) {
        meta.displayName(ColorUtil.component(displayName));
        return this;
    }

    public ItemBuilder name(Component displayName) {
        meta.displayName(displayName);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        meta.lore(lines.stream().map(ColorUtil::component).toList());
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    /**
     * Component-based overload for a lore line that needs to mix a colored prefix with a
     * translatable part (e.g. a vanilla material name resolved client-side to the viewing
     * player's own game language) - {@link #lore(List)} only accepts pre-colorized strings, so
     * a caller building a translatable line assembles the full {@link Component} itself
     * (typically {@code ColorUtil.component(prefix).append(Component.translatable(key))}) and
     * passes it here instead.
     */
    public ItemBuilder loreComponents(List<Component> lines) {
        meta.lore(lines);
        return this;
    }

    public ItemBuilder customModelData(int customModelData) {
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        return this;
    }

    /** Sets vanilla's own "Unbreakable" NBT flag - the item never loses durability, no matter how it's used. */
    public ItemBuilder unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return this;
    }

    /** Adds {@code enchantment} without showing it in the item's lore (HIDE_ENCHANTS) - for a mechanical-only effect baked into a weapon rather than a player-visible enchant. */
    public ItemBuilder hiddenEnchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public <T, Z> ItemBuilder tag(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
}
