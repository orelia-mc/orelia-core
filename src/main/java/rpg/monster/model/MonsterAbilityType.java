package rpg.monster.model;

/**
 * Which {@code rpg.monster.service.MonsterAbilityCastService} archetype runs a
 * {@link MonsterAbility}. Same archetypes {@code rpg.boss.model.BossAbilityType} offers -
 * kept as an independent enum rather than shared, matching the deliberate separation of
 * {@link MonsterAbility} from {@code BossAbility}.
 */
public enum MonsterAbilityType {
    /** Instant damage burst around the monster, with a heavy particle/sound flourish. */
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
