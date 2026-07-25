package rpg.item.model;

import org.bukkit.Material;
import rpg.job.model.JobType;

import java.util.List;

/**
 * Static weapon definition loaded from {@code items.yml} (SOW section 6). One instance
 * per weapon id; the fields here are the template used to stamp out {@link org.bukkit.inventory.ItemStack}s,
 * not per-copy state (enchantments/skill sockets live on the item's PersistentDataContainer).
 */
public final class WeaponData {

    private final String id;
    private final String name;
    private final WeaponType weaponType;
    private final int weaponLevel;
    private final Rarity rarity;
    private final double attackPower;
    private final ElementType element;
    private final double critRate;
    private final double critMultiplier;
    private final JobType requiredJob;
    private final int requiredLevel;
    private final List<String> description;
    private final int customModelData;
    private final double sellPrice;
    private final int skillSlotCount;
    private final boolean unbreakable;
    private final int bulkChopRadius;
    private final int gatherRequiredLevel;
    private final int luckLevel;
    private final Material baseMaterial;

    public WeaponData(String id, String name, WeaponType weaponType, int weaponLevel, Rarity rarity,
                       double attackPower, ElementType element, double critRate, double critMultiplier,
                       JobType requiredJob, int requiredLevel, List<String> description,
                       int customModelData, double sellPrice, int skillSlotCount, boolean unbreakable,
                       int bulkChopRadius, int gatherRequiredLevel, int luckLevel, Material baseMaterial) {
        this.id = id;
        this.name = name;
        this.weaponType = weaponType;
        this.weaponLevel = weaponLevel;
        this.rarity = rarity;
        this.attackPower = attackPower;
        this.element = element;
        this.critRate = critRate;
        this.critMultiplier = critMultiplier;
        this.requiredJob = requiredJob;
        this.requiredLevel = requiredLevel;
        this.description = description;
        this.customModelData = customModelData;
        this.sellPrice = sellPrice;
        this.skillSlotCount = skillSlotCount;
        this.unbreakable = unbreakable;
        this.bulkChopRadius = bulkChopRadius;
        this.gatherRequiredLevel = gatherRequiredLevel;
        this.luckLevel = luckLevel;
        this.baseMaterial = baseMaterial;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WeaponType getWeaponType() {
        return weaponType;
    }

    public int getWeaponLevel() {
        return weaponLevel;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public double getAttackPower() {
        return attackPower;
    }

    public ElementType getElement() {
        return element;
    }

    public double getCritRate() {
        return critRate;
    }

    public double getCritMultiplier() {
        return critMultiplier;
    }

    public JobType getRequiredJob() {
        return requiredJob;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public List<String> getDescription() {
        return description;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public int getSkillSlotCount() {
        return skillSlotCount;
    }

    public boolean isUnbreakable() {
        return unbreakable;
    }

    /**
     * (HATCHET only) radius of the automatic bulk-chop sweep this axe grants on every break -
     * 0 disables bulk chop (single-block only). Ignored for other weapon types.
     */
    public int getBulkChopRadius() {
        return bulkChopRadius;
    }

    /**
     * (HATCHET/PICKAXE only) minimum woodcutting/mining job level (see
     * {@code GatheringLevelService}, not character level) required to use this tool at all -
     * 0 means no restriction. Ignored for other weapon types.
     */
    public int getGatherRequiredLevel() {
        return gatherRequiredLevel;
    }

    /**
     * (PICKAXE only, default 0) each level rolls a chance (see {@code MiningLuckConfig}) per
     * ore block broken to add +1 to a random drop from that block. 0 = no bonus. Ignored for
     * other weapon types.
     */
    public int getLuckLevel() {
        return luckLevel;
    }

    /** The explicit {@code items.yml} base-material override, or null to derive it from rarity. */
    public Material getBaseMaterial() {
        return baseMaterial;
    }

    /**
     * The vanilla item this weapon is stamped onto. An explicit {@code base-material} wins;
     * otherwise it falls back to {@link WeaponType#materialForRarity(Rarity)}. Gathering tools
     * generally want the explicit form, since a vanilla material's harvest tier (which decides
     * what ore it can actually collect) is unrelated to how rare the weapon is meant to feel.
     */
    public Material resolveBaseMaterial() {
        return baseMaterial != null ? baseMaterial : weaponType.materialForRarity(rarity);
    }
}
