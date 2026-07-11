package rpg.boss.model;

/**
 * Which {@code rpg.boss.service.BossAbilityCastService} archetype runs a {@link BossAbility}.
 */
public enum BossAbilityType {
    /** Instant damage burst around the boss, with a heavy particle/sound flourish. */
    AOE_SLAM,
    /** Fires a volley of homing-ish fireballs at every nearby player. */
    FIREBALL_BARRAGE
}
