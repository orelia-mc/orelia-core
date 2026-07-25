package rpg.gathering.service;

import rpg.gathering.config.LevelRadiusConfig;

/**
 * Single source of truth for how big a bulk-gather sweep applies, replacing three
 * independent reimplementations of the same rules (GatherBlockBreakListener's mining and
 * woodcutting branches, FarmingListener#onPlant, FarmingListener#onHarvest) - see
 * rpg.status.combat.DamageFormula for the precedent this follows. Pure, no Bukkit
 * dependency, mirroring LevelRadiusConfig's own test-friendly shape.
 */
public final class BulkRadiusResolver {

    private BulkRadiusResolver() {
    }

    /**
     * Mining and woodcutting share this rule: radius and its own level gate come entirely
     * from the equipped tool's (PICKAXE/HATCHET) own items.yml fields, no sneak requirement.
     * Pass (0, 0) when no matching tool is equipped.
     */
    public static int equippedToolRadius(int level, int toolBulkRadius, int toolRequiredLevel) {
        return level >= toolRequiredLevel ? toolBulkRadius : 0;
    }

    /**
     * Farming's rule: bulk only triggers while sneaking, sized by the shared
     * {@link LevelRadiusConfig}.
     */
    public static int levelBasedRadius(boolean sneaking, int level, LevelRadiusConfig radiusConfig) {
        if (!sneaking) {
            return 0;
        }
        return radiusConfig.radiusForLevel(level);
    }
}
