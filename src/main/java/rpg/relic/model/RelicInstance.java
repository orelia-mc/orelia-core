package rpg.relic.model;

import rpg.accessory.model.AccessoryType;

import java.util.List;
import java.util.UUID;

/**
 * One rolled relic's full state, read from / written to an {@link org.bukkit.inventory.ItemStack}'s
 * PersistentDataContainer by {@code RelicIdentityService} - distinct from the config-driven,
 * fully static {@code rpg.accessory.model.AccessoryData} that ordinary shop accessories use.
 *
 * @param level        0-15; grows 3 levels at a time via {@code RelicUpgradeService}.
 * @param substats     up to 4 lines, unlocked/grown one at a time as the player chooses at each
 *                     upgrade (see {@code RelicUpgradeService}) - never duplicates {@code mainStat}'s type.
 * @param sourceDungeonId the dungeon whose boss dropped this relic - drives the 2-piece set
 *                        bonus in {@code relics.yml}'s {@code dungeon-set-bonuses}.
 */
public record RelicInstance(UUID instanceId, AccessoryType part, RelicLine mainStat,
                             List<RelicLine> substats, int level, String sourceDungeonId) {

    public RelicInstance {
        substats = List.copyOf(substats);
    }

    public RelicInstance withLevel(int newLevel) {
        return new RelicInstance(instanceId, part, mainStat, substats, newLevel, sourceDungeonId);
    }

    public RelicInstance withSubstats(List<RelicLine> newSubstats) {
        return new RelicInstance(instanceId, part, mainStat, newSubstats, level, sourceDungeonId);
    }
}
