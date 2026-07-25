package rpg.gathering.model;

import org.bukkit.Sound;
import rpg.job.model.JobType;

/**
 * Which gathering activity a block/crop belongs to (SOW 3.1 {@code gather-settings}
 * top-level keys, plus {@code FARMING} for {@code farm-settings} crops and {@code FISHING}
 * for {@code fishing.yml}). Each activity levels up its own {@link JobType} independently -
 * mining raises the miner level, woodcutting the woodcutter level, farming the farmer
 * level, fishing the fisherman level - rather than all of them feeding one shared
 * "gathering level" pool.
 */
public enum GatherActionType {
    MINING(JobType.MINER, Sound.BLOCK_STONE_BREAK),
    WOODCUTTING(JobType.WOODCUTTER, Sound.BLOCK_WOOD_BREAK),
    FARMING(JobType.FARMER, Sound.BLOCK_CROP_BREAK),
    FISHING(JobType.FISHERMAN, Sound.ENTITY_FISHING_BOBBER_SPLASH);

    private final JobType jobType;
    private final Sound breakSound;

    GatherActionType(JobType jobType, Sound breakSound) {
        this.jobType = jobType;
        this.breakSound = breakSound;
    }

    public JobType jobType() {
        return jobType;
    }

    public Sound breakSound() {
        return breakSound;
    }
}
