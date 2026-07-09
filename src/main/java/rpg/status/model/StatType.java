package rpg.status.model;

/**
 * The core stats every player, monster and equipment bonus is expressed in (SOW section
 * 10). CRT is critical hit rate; CRT_DMG is the separate critical hit damage bonus -
 * don't conflate the two. ATK is the melee/thrown damage multiplier; BOW_ATK is the
 * equivalent for bow/crossbow arrow damage, since ranged attacks land as a separate
 * damage event fired by the arrow rather than the player.
 */
public enum StatType {
    HP,
    SP,
    ATK,
    BOW_ATK,
    DEF,
    CRT,
    CRT_DMG,
    SPD
}
