package rpg.item.model;

import org.bukkit.Material;

import java.util.List;

/**
 * The seven weapon categories in scope for this project (SOW section 6, extended for the
 * gathering jobs). Staffs/grimoires are intentionally not modeled yet. Job and skill
 * modules reference this enum to express weapon restrictions and skill trees without
 * redefining their own copy.
 *
 * <p>HATCHET is a deliberately separate category from AXE, not a shared one - AXE is the
 * warrior's weapon and HATCHET is the woodcutter's, so both jobs keep a strictly 1:1
 * job-to-weapon-type mapping even though they reuse the same vanilla axe materials for
 * their base item. Which category a given {@code ItemStack} belongs to is never inferred
 * from its {@link Material} - only from the id tag {@link rpg.item.service.WeaponFactory}
 * stamps on it at creation (see {@link rpg.item.service.WeaponIdentityService#dataOf}) - so
 * reusing the same base materials across two types never causes them to be confused with
 * each other, and a plain vanilla tool with no such tag is never treated as either.
 */
public enum WeaponType {
    SWORD(List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD)),
    SPEAR(List.of(Material.TRIDENT)),
    AXE(List.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE)),
    BOW(List.of(Material.BOW, Material.CROSSBOW)),
    PICKAXE(List.of(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE)),
    HOE(List.of(Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE)),
    HATCHET(List.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE));

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
