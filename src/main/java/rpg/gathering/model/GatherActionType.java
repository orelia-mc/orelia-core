package rpg.gathering.model;

import rpg.job.model.JobType;

/**
 * Which gathering activity a block/crop belongs to (SOW 3.1 {@code gather-settings}
 * top-level keys, plus {@code FARMING} for {@code farm-settings} crops). Each activity
 * levels up its own {@link JobType} independently - mining raises the miner level,
 * woodcutting the woodcutter level, farming the farmer level - rather than all three
 * feeding one shared "gathering level" pool.
 */
public enum GatherActionType {
    MINING(JobType.MINER),
    WOODCUTTING(JobType.WOODCUTTER),
    FARMING(JobType.FARMER);

    private final JobType jobType;

    GatherActionType(JobType jobType) {
        this.jobType = jobType;
    }

    public JobType jobType() {
        return jobType;
    }
}
