package rpg.boss.model;

/**
 * Which {@code rpg.boss.service.BossAbilityCastService} archetype runs a {@link BossAbility}.
 */
public enum BossAbilityType {
    /** Instant damage burst around the boss, with a heavy particle/sound flourish. */
    AOE_SLAM,
    /** Fires a volley of homing-ish fireballs at every nearby player. */
    FIREBALL_BARRAGE,
    /** Teleports next to a random nearby player - a repositioning ambush, not a direct attack. */
    TELEPORT,
    /** Applies a potion effect to every player within range - a status-inflicting attack with no direct damage. */
    DEBUFF,
    /** Spawns a handful of another monsters.yml entry's monsters nearby as reinforcements. */
    SUMMON
}
