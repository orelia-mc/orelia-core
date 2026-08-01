package rpg.extra.mount.manager;

import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each player's currently-spawned mount entity (SOW MountModule). Purely in-memory -
 * spawned mounts don't survive a server restart, only the ownership/selection records do.
 */
public final class MountManager {

    private final Map<UUID, Entity> activeEntities = new ConcurrentHashMap<>();

    public void register(UUID ownerId, Entity entity) {
        despawn(ownerId);
        activeEntities.put(ownerId, entity);
    }

    public void despawn(UUID ownerId) {
        Entity existing = activeEntities.remove(ownerId);
        if (existing != null && !existing.isDead()) {
            existing.remove();
        }
    }

    public boolean hasActiveMount(UUID ownerId) {
        Entity entity = activeEntities.get(ownerId);
        return entity != null && !entity.isDead();
    }

    public boolean isTrackedMount(Entity entity) {
        return activeEntities.containsValue(entity);
    }

    public void despawnAll() {
        activeEntities.values().forEach(entity -> {
            if (!entity.isDead()) {
                entity.remove();
            }
        });
        activeEntities.clear();
    }
}
