package rpg.monster.model;

/**
 * One ability a regular (non-boss) monster periodically casts at nearby players. Deliberately
 * separate from {@code rpg.boss.model.BossAbility} - regular monsters have no phase/enrage
 * concerns a boss does - and separate from {@code rpg.skill.model.SkillData} for the same
 * reason {@code BossAbility} is: monsters have no MP, weapon, or socket, so they need their
 * own lightweight cast/cooldown model instead of reusing the player skill system.
 *
 * <p>{@code effectType}/{@code effectDurationSeconds}/{@code effectAmplifier} are only read for
 * {@link MonsterAbilityType#DEBUFF}; {@code summonMonsterId}/{@code summonCount} are only read
 * for {@link MonsterAbilityType#SUMMON}. Left blank/zero (the config defaults) for every other
 * type rather than split into a per-type subclass - one flat shape with per-type unused fields
 * is the same tradeoff {@code damage}/{@code radius} already make (each existing type already
 * reads them for a different purpose - see {@code MonsterAbilityCastService}'s per-type cast
 * methods for which fields a given {@link MonsterAbilityType} actually reads).
 */
public final class MonsterAbility {

    private final String id;
    private final String name;
    private final MonsterAbilityType type;
    private final double damage;
    private final double radius;
    private final int cooldownSeconds;
    private final String particle;
    private final String sound;
    private final String effectType;
    private final int effectDurationSeconds;
    private final int effectAmplifier;
    private final String summonMonsterId;
    private final int summonCount;

    public MonsterAbility(String id, String name, MonsterAbilityType type, double damage, double radius,
                           int cooldownSeconds, String particle, String sound, String effectType,
                           int effectDurationSeconds, int effectAmplifier, String summonMonsterId, int summonCount) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.damage = damage;
        this.radius = radius;
        this.cooldownSeconds = cooldownSeconds;
        this.particle = particle;
        this.sound = sound;
        this.effectType = effectType;
        this.effectDurationSeconds = effectDurationSeconds;
        this.effectAmplifier = effectAmplifier;
        this.summonMonsterId = summonMonsterId;
        this.summonCount = summonCount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public MonsterAbilityType getType() {
        return type;
    }

    /** Multiplier on this monster's own (level-scaled) attack power - see {@code rpg.monster.service.MonsterAbilityCastService#abilityDamage}. */
    public double getDamage() {
        return damage;
    }

    public double getRadius() {
        return radius;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public String getParticle() {
        return particle;
    }

    public String getSound() {
        return sound;
    }

    /** {@link org.bukkit.potion.PotionEffectType} key name (e.g. {@code POISON}) - only meaningful for {@link MonsterAbilityType#DEBUFF}. */
    public String getEffectType() {
        return effectType;
    }

    public int getEffectDurationSeconds() {
        return effectDurationSeconds;
    }

    public int getEffectAmplifier() {
        return effectAmplifier;
    }

    /** monsters.yml id of the monster spawned as reinforcements - only meaningful for {@link MonsterAbilityType#SUMMON}. */
    public String getSummonMonsterId() {
        return summonMonsterId;
    }

    public int getSummonCount() {
        return summonCount;
    }
}
