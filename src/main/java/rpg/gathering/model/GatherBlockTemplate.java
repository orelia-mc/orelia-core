package rpg.gathering.model;

import org.bukkit.Material;

/**
 * Config-driven definition of one gatherable block (SOW 3.1 {@code gather-settings.*}).
 */
public record GatherBlockTemplate(
        Material blockType,
        GatherActionType actionType,
        int cooldownSeconds,
        Material replaceBlock,
        int xpGain,
        int minLevel) {
}
