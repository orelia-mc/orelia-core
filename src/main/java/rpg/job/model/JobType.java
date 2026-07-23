package rpg.job.model;

/**
 * Initial job roster (SOW section 9). New jobs still require a code change here since
 * job identity drives weapon-restriction and skill-tree logic, unlike weapons/skills
 * which are fully config-driven.
 */
public enum JobType {
    FENCER,
    WARRIOR,
    ARCHER,
    MINER,
    FARMER,
    WOODCUTTER
}
