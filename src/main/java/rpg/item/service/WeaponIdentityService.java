package rpg.item.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import rpg.item.model.WeaponData;
import rpg.item.repository.WeaponRepository;

import java.util.Optional;

/**
 * Resolves an in-game {@link ItemStack} back to the {@link WeaponData} template it was
 * generated from, by reading the id stamped by {@link WeaponFactory}.
 */
public final class WeaponIdentityService {

    private final WeaponKeys keys;
    private final WeaponRepository repository;

    public WeaponIdentityService(WeaponKeys keys, WeaponRepository repository) {
        this.keys = keys;
        this.repository = repository;
    }

    public Optional<String> idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        String id = meta.getPersistentDataContainer().get(keys.weaponId(), PersistentDataType.STRING);
        return Optional.ofNullable(id);
    }

    public Optional<WeaponData> dataOf(ItemStack stack) {
        return idOf(stack).flatMap(repository::findById);
    }

    /** Enhancement level applied by the "強化屋" NPC (SOW section 12), 0 for a freshly created weapon. */
    public int getEnhancementLevel(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Integer level = stack.getItemMeta().getPersistentDataContainer().get(keys.enhancementLevel(), PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    /** Increments the weapon's enhancement level by one and returns the new level. */
    public int enhance(ItemStack stack) {
        int newLevel = getEnhancementLevel(stack) + 1;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(keys.enhancementLevel(), PersistentDataType.INTEGER, newLevel);
        stack.setItemMeta(meta);
        return newLevel;
    }

    /** Attack-power scale factor from enhancement level: +10% per level. */
    public double enhancementMultiplier(ItemStack stack) {
        return 1.0 + getEnhancementLevel(stack) * 0.1;
    }
}
