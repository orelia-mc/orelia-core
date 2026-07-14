package rpg.skill.model;

import rpg.item.model.WeaponType;

/**
 * Static skill definition loaded from {@code skills.yml} (SOW section 7). {@code executorType}
 * selects which {@link rpg.skill.executor.SkillExecutor} implementation runs the skill,
 * so most new skills only need a new config entry, reusing an existing executor archetype.
 */
public final class SkillData {

    private final String id;
    private final String name;
    private final WeaponType weaponType;
    private final String executorType;
    private final double spCost;
    private final double cooldownSeconds;
    private final double damageMultiplier;
    private final String effectParticle;
    private final double range;
    private final double radius;
    private final double knockback;
    private final int maxLevel;

    public SkillData(String id, String name, WeaponType weaponType, String executorType, double spCost,
                      double cooldownSeconds, double damageMultiplier, String effectParticle,
                      double range, double radius, double knockback, int maxLevel) {
        this.id = id;
        this.name = name;
        this.weaponType = weaponType;
        this.executorType = executorType;
        this.spCost = spCost;
        this.cooldownSeconds = cooldownSeconds;
        this.damageMultiplier = damageMultiplier;
        this.effectParticle = effectParticle;
        this.range = range;
        this.radius = radius;
        this.knockback = knockback;
        this.maxLevel = maxLevel;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WeaponType getWeaponType() {
        return weaponType;
    }

    public String getExecutorType() {
        return executorType;
    }

    public double getSpCost() {
        return spCost;
    }

    public double getCooldownSeconds() {
        return cooldownSeconds;
    }

    public String getEffectParticle() {
        return effectParticle;
    }

    public double getRange() {
        return range;
    }

    public double getRadius() {
        return radius;
    }

    public double getKnockback() {
        return knockback;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /** Damage multiplier scaled by the caster's current skill level (level 1 = base multiplier). */
    public double scaledDamageMultiplier(int skillLevel) {
        return damageMultiplier * (1 + 0.1 * Math.max(0, skillLevel - 1));
    }
}
