package rpg.status.model;

/**
 * The core stats every player, monster and equipment bonus is expressed in
 * (SOW section 10). CRT is critical hit rate; CRT_DMG is the separate critical
 * hit damage bonus - don't conflate the two.
 */
public enum StatType {
    HP,
    SP,
    ATK,
    DEF,
    AGI,
    DEX,
    INT,
    CRT,
    CRT_DMG,
    SPD
}
