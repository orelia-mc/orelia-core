package rpg.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
        meta.setDisplayName(ColorUtil.colorize(displayName));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        meta.setLore(ColorUtil.colorize(lines));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder customModelData(int customModelData) {
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
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
