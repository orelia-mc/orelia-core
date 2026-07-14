package rpg.api;

import rpg.item.model.WeaponType;
import rpg.job.manager.JobManager;
import rpg.job.model.JobType;
import rpg.job.service.JobService;

import java.util.Optional;
import java.util.UUID;

final class JobApiImpl implements JobApi {

    private final JobService jobService;
    private final JobManager jobManager;

    JobApiImpl(JobService jobService, JobManager jobManager) {
        this.jobService = jobService;
        this.jobManager = jobManager;
    }

    @Override
    public Optional<String> getCurrentJob(UUID playerId) {
        return jobService.getCurrentJob(playerId).map(Enum::name);
    }

    @Override
    public Optional<String> getCurrentJobDisplayName(UUID playerId) {
        return jobService.getCurrentJob(playerId)
                .flatMap(jobManager::getDefinition)
                .map(job -> job.getDisplayName());
    }

    @Override
    public boolean canUseWeaponType(UUID playerId, String weaponType) {
        try {
            return jobService.canUseWeaponType(playerId, WeaponType.valueOf(weaponType.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean changeJob(UUID playerId, String jobName) {
        try {
            return jobService.changeJob(playerId, JobType.valueOf(jobName.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
