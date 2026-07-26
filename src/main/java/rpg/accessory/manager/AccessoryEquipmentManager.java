package rpg.accessory.manager;

import rpg.accessory.model.PlayerAccessoryEquipmentComponent;
import rpg.accessory.repository.AccessoryEquipmentRepository;
import rpg.core.player.PlayerDataComponentLoader;

import java.util.UUID;

/**
 * Bridges per-player accessory/relic equip state to Core's player data lifecycle - a thin
 * loader delegating to {@link AccessoryEquipmentRepository}, same shape as
 * {@code rpg.job.manager.JobManager}.
 */
public final class AccessoryEquipmentManager implements PlayerDataComponentLoader<PlayerAccessoryEquipmentComponent> {

    private final AccessoryEquipmentRepository repository;

    public AccessoryEquipmentManager(AccessoryEquipmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<PlayerAccessoryEquipmentComponent> type() {
        return PlayerAccessoryEquipmentComponent.class;
    }

    @Override
    public PlayerAccessoryEquipmentComponent loadOrCreate(UUID uuid) {
        return repository.loadOrCreate(uuid);
    }

    @Override
    public void save(PlayerAccessoryEquipmentComponent component) {
        repository.save(component);
    }
}
