package rpg.job.manager;

import rpg.core.player.PlayerDataComponentLoader;
import rpg.job.model.Job;
import rpg.job.model.JobType;
import rpg.job.model.PlayerJobComponent;
import rpg.job.repository.PlayerJobRepository;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds the static {@link Job} registry (loaded from jobs.yml) and bridges per-player
 * job selection to Core's player data lifecycle.
 */
public final class JobManager implements PlayerDataComponentLoader<PlayerJobComponent> {

    private final PlayerJobRepository repository;
    private Map<JobType, Job> definitions = new EnumMap<>(JobType.class);

    public JobManager(PlayerJobRepository repository) {
        this.repository = repository;
    }

    public void setDefinitions(Map<JobType, Job> definitions) {
        this.definitions = definitions;
    }

    public Optional<Job> getDefinition(JobType type) {
        return Optional.ofNullable(definitions.get(type));
    }

    @Override
    public Class<PlayerJobComponent> type() {
        return PlayerJobComponent.class;
    }

    @Override
    public PlayerJobComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(PlayerJobComponent component) {
        repository.save(component);
    }
}
