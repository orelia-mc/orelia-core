package rpg.boss.model;

/**
 * One skill a boss periodically casts at nearby players (SOW follow-up: "スキルを発動する
 * ボス"). Deliberately separate from {@code rpg.skill.model.SkillData} - bosses have no MP,
 * weapon, or socket, so they need their own lightweight cast/cooldown model instead of
 * reusing the player skill system.
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

    public BossAbility(String id, String name, BossAbilityType type, double damage, double radius,
                        int cooldownSeconds, String particle, String sound, String announceMessage) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.damage = damage;
        this.radius = radius;
        this.cooldownSeconds = cooldownSeconds;
        this.particle = particle;
        this.sound = sound;
        this.announceMessage = announceMessage;
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
}
