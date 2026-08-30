package rpg.boss.model;

/**
 * One skill a boss periodically casts at nearby players (SOW follow-up: "スキルを発動する
 * ボス"). Deliberately separate from {@code rpg.skill.model.SkillData} - bosses have no MP,
 * weapon, or socket, so they need their own lightweight cast/cooldown model instead of
 * reusing the player skill system.
 *
 * <p>{@code effectType}/{@code effectDurationSeconds}/{@code effectAmplifier} are only read for
 * {@link BossAbilityType#DEBUFF}; {@code summonMonsterId}/{@code summonCount} are only read for
 * {@link BossAbilityType#SUMMON}. Left blank/zero (the config defaults) for every other type -
 * same one-flat-shape tradeoff {@code damage}/{@code radius} already make. See
 * {@code rpg.monster.model.MonsterAbility}'s own copy of this same note for why this stays a
 * separate class rather than a shared one.
 */
public final class BossAbility {

    private final String id;
    private final String name;
    private final BossAbilityType type;
    private final double damage;
    private final double radius;
    private final int cooldownSeconds;
    private final String particle;
    private final String sound;
    private final String announceMessage;
    private final String effectType;
    private final int effectDurationSeconds;
    private final int effectAmplifier;
    private final String summonMonsterId;
    private final int summonCount;

    public BossAbility(String id, String name, BossAbilityType type, double damage, double radius,
                        int cooldownSeconds, String particle, String sound, String announceMessage,
                        String effectType, int effectDurationSeconds, int effectAmplifier,
                        String summonMonsterId, int summonCount) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.damage = damage;
        this.radius = radius;
        this.cooldownSeconds = cooldownSeconds;
        this.particle = particle;
        this.sound = sound;
        this.announceMessage = announceMessage;
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

    public BossAbilityType getType() {
        return type;
    }

    /** Multiplier on this boss's own (level-scaled) attack power - see {@code rpg.boss.service.BossAbilityCastService#abilityDamage}. */
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

    public String getAnnounceMessage() {
        return announceMessage;
    }

    /** {@link org.bukkit.potion.PotionEffectType} key name (e.g. {@code POISON}) - only meaningful for {@link BossAbilityType#DEBUFF}. */
    public String getEffectType() {
        return effectType;
    }

    public int getEffectDurationSeconds() {
        return effectDurationSeconds;
    }

    public int getEffectAmplifier() {
        return effectAmplifier;
    }

    /** monsters.yml id of the monster spawned as reinforcements - only meaningful for {@link BossAbilityType#SUMMON}. */
    public String getSummonMonsterId() {
        return summonMonsterId;
    }

    public int getSummonCount() {
        return summonCount;
    }
}
