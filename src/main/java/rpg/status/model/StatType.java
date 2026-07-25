package rpg.status.model;

/**
 * The core stats every player, monster and equipment bonus is expressed in
 * (SOW section 10). CRT is critical hit rate; CRT_DMG is the separate critical
 * hit damage bonus - don't conflate the two.
 *
 * <p>{@code FIRE_DMG}/{@code WATER_DMG}/{@code EARTH_DMG}/{@code WIND_DMG}/{@code LIGHT_DMG}/
 * {@code DARK_DMG} (percent bonus to damage dealt with a weapon of the matching
 * {@link rpg.item.model.ElementType}, see {@code rpg.status.combat.DamageFormula}) and
 * {@code SP_RECOVERY} (percent bonus to SP regen, see
 * {@code rpg.status.service.StatusService#tickRegen}) exist only to be granted by relics -
 * nothing else in the base game ever sets them.
 */
public enum StatType {
    HP,
    SP,
    ATK,
    DEF,
    CRT,
    CRT_DMG,
    SPD,
    FIRE_DMG,
    WATER_DMG,
    EARTH_DMG,
    WIND_DMG,
    LIGHT_DMG,
    DARK_DMG,
    SP_RECOVERY
}
