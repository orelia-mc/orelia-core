package rpg.job.model;

/**
 * Initial job roster (SOW section 9). New jobs still require a code change here since
 * job identity drives weapon-restriction and skill-tree logic, unlike weapons/skills
 * which are fully config-driven.
 */
public enum JobType {
    SWORDSMAN,
    SPEARMAN,
    WARRIOR,
    ARCHER,
    // Gathering job (SOW: 採取・農業拡張). Stat/leveling bonuses for fishing are still
    // being designed - this is intentionally just a job entry with no passive bonus yet.
    FISHERMAN
}
