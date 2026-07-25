package rpg.relic.model;

import rpg.status.model.ModifierType;
import rpg.status.model.StatType;

/**
 * The 9 stat lines a relic's main stat / substats can roll (see {@code relics.yml}'s
 * per-part main-stat pools). {@link #ELEMENTAL_DMG_PERCENT} is special: it doesn't map to a
 * single fixed {@link StatType} on its own - a concrete element (see
 * {@code rpg.item.model.ElementType}) is rolled alongside it at generation time and the two
 * together resolve to one of {@code StatType.FIRE_DMG}/{@code WATER_DMG}/etc (see
 * {@code rpg.relic.service.RelicStatResolver}).
 */
public enum RelicStatType {
    HP_PERCENT(StatType.HP, ModifierType.PERCENT, "HP%"),
    DEF_PERCENT(StatType.DEF, ModifierType.PERCENT, "防御力%"),
    ATK_PERCENT(StatType.ATK, ModifierType.PERCENT, "攻撃力%"),
    CRT_PERCENT(StatType.CRT, ModifierType.FLAT, "会心率%"),
    CRT_DMG_PERCENT(StatType.CRT_DMG, ModifierType.FLAT, "会心ダメージ%"),
    ELEMENTAL_DMG_PERCENT(null, ModifierType.FLAT, "属性ダメージ増加%"),
    SP_RECOVERY_PERCENT(StatType.SP_RECOVERY, ModifierType.FLAT, "SP回復効率%"),
    HP_FLAT(StatType.HP, ModifierType.FLAT, "HP"),
    ATK_FLAT(StatType.ATK, ModifierType.FLAT, "攻撃力");

    private final StatType fixedStatType;
    private final ModifierType modifierType;
    private final String displayLabel;

    RelicStatType(StatType fixedStatType, ModifierType modifierType, String displayLabel) {
        this.fixedStatType = fixedStatType;
        this.modifierType = modifierType;
        this.displayLabel = displayLabel;
    }

    /** {@code null} only for {@link #ELEMENTAL_DMG_PERCENT} - resolve via {@code RelicStatResolver} instead. */
    public StatType getFixedStatType() {
        return fixedStatType;
    }

    public ModifierType getModifierType() {
        return modifierType;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }
}
