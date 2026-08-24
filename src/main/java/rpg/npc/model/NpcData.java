package rpg.npc.model;

import org.bukkit.entity.EntityType;
import rpg.api.ShopEntry;

import java.util.List;

/**
 * Static NPC definition loaded from {@code npc.yml} (SOW section 12). Which fields are
 * meaningful depends on {@link #getType()}: shop NPCs use {@link #getShopStock()}, the
 * quest receptionist uses {@link #getQuestIds()}, the enhancement NPC uses the enhance-
 * cost fields, the weapon-levelup NPC uses the weapon-levelup fields (a plain vanilla
 * material + amount, plus a cost that scales the same way enhancement's does), and every
 * type can show {@link #getDialogueLines()} plus an optional alternate line when the player
 * holds {@link #getConditionalItemId()} ("アイテム所持で会話変化").
 */
public final class NpcData {

    private final String id;
    private final String name;
    private final NpcType type;
    private final EntityType entityType;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final List<String> dialogueLines;
    private final String conditionalItemId;
    private final List<String> conditionalDialogueLines;
    private final List<ShopEntry> shopStock;
    private final List<String> questIds;
    private final double enhancementCostBase;
    private final double enhancementCostPerLevel;
    private final String weaponLevelupItemMaterial;
    private final int weaponLevelupItemAmount;
    private final double weaponLevelupCostBase;
    private final double weaponLevelupCostPerLevel;

    public NpcData(String id, String name, NpcType type, EntityType entityType, String world, double x, double y, double z,
                   float yaw, List<String> dialogueLines, String conditionalItemId, List<String> conditionalDialogueLines,
                   List<ShopEntry> shopStock, List<String> questIds, double enhancementCostBase, double enhancementCostPerLevel,
                   String weaponLevelupItemMaterial, int weaponLevelupItemAmount, double weaponLevelupCostBase,
                   double weaponLevelupCostPerLevel) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.entityType = entityType;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.dialogueLines = dialogueLines;
        this.conditionalItemId = conditionalItemId;
        this.conditionalDialogueLines = conditionalDialogueLines;
        this.shopStock = shopStock;
        this.questIds = questIds;
        this.enhancementCostBase = enhancementCostBase;
        this.enhancementCostPerLevel = enhancementCostPerLevel;
        this.weaponLevelupItemMaterial = weaponLevelupItemMaterial;
        this.weaponLevelupItemAmount = weaponLevelupItemAmount;
        this.weaponLevelupCostBase = weaponLevelupCostBase;
        this.weaponLevelupCostPerLevel = weaponLevelupCostPerLevel;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public NpcType getType() {
        return type;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public List<String> getDialogueLines() {
        return dialogueLines;
    }

    public String getConditionalItemId() {
        return conditionalItemId;
    }

    public List<String> getConditionalDialogueLines() {
        return conditionalDialogueLines;
    }

    public List<ShopEntry> getShopStock() {
        return shopStock;
    }

    public List<String> getQuestIds() {
        return questIds;
    }

    public double getEnhancementCostBase() {
        return enhancementCostBase;
    }

    public double getEnhancementCostPerLevel() {
        return enhancementCostPerLevel;
    }

    public String getWeaponLevelupItemMaterial() {
        return weaponLevelupItemMaterial;
    }

    public int getWeaponLevelupItemAmount() {
        return weaponLevelupItemAmount;
    }

    public double getWeaponLevelupCostBase() {
        return weaponLevelupCostBase;
    }

    public double getWeaponLevelupCostPerLevel() {
        return weaponLevelupCostPerLevel;
    }
}
