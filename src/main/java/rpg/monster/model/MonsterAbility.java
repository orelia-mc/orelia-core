package rpg.monster.model;

/**
 * One ability a regular (non-boss) monster periodically casts at nearby players. Deliberately
 * separate from {@code rpg.boss.model.BossAbility} - regular monsters have no phase/enrage
 * concerns a boss does - and separate from {@code rpg.skill.model.SkillData} for the same
 * reason {@code BossAbility} is: monsters have no MP, weapon, or socket, so they need their
 * own lightweight cast/cooldown model instead of reusing the player skill system.
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

    public MonsterAbility(String id, String name, MonsterAbilityType type, double damage, double radius,
                           int cooldownSeconds, String particle, String sound) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.damage = damage;
        this.radius = radius;
        this.cooldownSeconds = cooldownSeconds;
        this.particle = particle;
        this.sound = sound;
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
}
