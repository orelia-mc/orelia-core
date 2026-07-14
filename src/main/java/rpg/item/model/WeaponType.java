package rpg.item.model;

import org.bukkit.Material;

import java.util.List;

/**
 * The four weapon categories in scope for this project (SOW section 6). Staffs/grimoires
 * are intentionally not modeled yet. Job and skill modules reference this enum to express
 * weapon restrictions and skill trees without redefining their own copy.
 */
public enum WeaponType {
    SWORD(List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD)),
    SPEAR(List.of(Material.TRIDENT)),
    AXE(List.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE)),
    BOW(List.of(Material.BOW, Material.CROSSBOW));

    /** Weakest to strongest, so a weapon's {@link Rarity} can be mapped onto a fitting base material. */
    private final List<Material> baseMaterialsByTier;

    WeaponType(List<Material> baseMaterialsByTier) {
        this.baseMaterialsByTier = baseMaterialsByTier;
    }

    /** Picks a base material for the given rarity, clamping to the strongest tier this weapon type has. */
    public Material materialForRarity(Rarity rarity) {
        int index = Math.min(rarity.ordinal(), baseMaterialsByTier.size() - 1);
        return baseMaterialsByTier.get(index);
    }
}
