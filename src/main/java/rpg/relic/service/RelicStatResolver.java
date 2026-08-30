package rpg.relic.service;

import rpg.relic.model.RelicLine;
import rpg.relic.model.RelicStatType;
import rpg.status.model.StatType;

/**
 * Resolves a {@link RelicLine} down to the concrete {@link StatType} it grants a
 * {@code rpg.status.service.StatusService#addBuff} call for - the one place that knows how to
 * turn {@code ELEMENTAL_DMG_PERCENT + ElementType.FIRE} into {@code StatType.FIRE_DMG}.
 */
public final class RelicStatResolver {

    private RelicStatResolver() {
    }

    public static StatType resolveStatType(RelicLine line) {
        StatType fixed = line.type().getFixedStatType();
        if (fixed != null) {
            return fixed;
        }
        return switch (line.element()) {
            case FIRE -> StatType.FIRE_DMG;
            case WATER -> StatType.WATER_DMG;
            case EARTH -> StatType.EARTH_DMG;
            case WIND -> StatType.WIND_DMG;
            case LIGHT -> StatType.LIGHT_DMG;
            case DARK -> StatType.DARK_DMG;
            case NONE -> throw new IllegalStateException("ELEMENTAL_DMG_PERCENT relic line rolled with ElementType.NONE");
        };
    }

    /** Human-readable line label, e.g. "会心率%" or "属性ダメージ増加%(火)" for elemental lines. */
    public static String describe(RelicLine line) {
        if (line.type() == RelicStatType.ELEMENTAL_DMG_PERCENT) {
            return line.type().getDisplayLabel() + "(" + line.element().getDisplayLabel() + ")";
        }
        return line.type().getDisplayLabel();
    }
}
