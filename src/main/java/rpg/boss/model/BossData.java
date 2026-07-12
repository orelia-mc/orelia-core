package rpg.boss.model;

import java.util.List;

/**
 * Static boss definition loaded from {@code bosses.yml}. A boss wraps an existing
 * {@code monsters.yml} entry ({@link #getMonsterId()}) rather than redefining HP/attack/
 * defense/drops, so spawning, per-hit combat math, and death rewards all reuse the
 * monster module's pipeline (SOW coding rule: no duplicated cross-module logic). This
 * module only layers phases and an enrage multiplier on top.
 */
public final class BossData {

    private final String id;
    private final String monsterId;
    private final List<BossPhase> phases;
    private final double enrageHpPercent;
    private final double enrageDamageMultiplier;
    private final List<BossAbility> abilities;

    public BossData(String id, String monsterId, List<BossPhase> phases, double enrageHpPercent,
                     double enrageDamageMultiplier, List<BossAbility> abilities) {
        this.id = id;
        this.monsterId = monsterId;
        this.phases = phases;
        this.enrageHpPercent = enrageHpPercent;
        this.enrageDamageMultiplier = enrageDamageMultiplier;
        this.abilities = abilities;
    }

    public String getId() {
        return id;
    }

    public String getMonsterId() {
        return monsterId;
    }

    /** Phases sorted by descending HP threshold - the first phase whose threshold has not fired yet is "next". */
    public List<BossPhase> getPhases() {
        return phases;
    }

    public double getEnrageHpPercent() {
        return enrageHpPercent;
    }

    public double getEnrageDamageMultiplier() {
        return enrageDamageMultiplier;
    }

    public List<BossAbility> getAbilities() {
        return abilities;
    }
}
