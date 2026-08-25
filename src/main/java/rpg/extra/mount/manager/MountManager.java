package rpg.extra.mount.manager;

import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks each player's currently-spawned mount entity (SOW MountModule). Purely in-memory -
 * spawned mounts don't survive a server restart, only the ownership/selection records do.
 */
public final class MountManager {

    private final Map<UUID, Entity> activeEntities = new ConcurrentHashMap<>();
    /**
     * Called at the top of every {@link #despawn} (including from {@link #despawnAll}), so
     * anything hooked onto "this player's mount is no longer active" - e.g.
     * {@code MountGrowthService#clearGrowthBonus} - fires through every despawn path (dismount,
     * {@code /ol mount dismiss}, quit, server shutdown) rather than only the one a caller
     * happens to remember to call it from.
     */
    private Consumer<UUID> onDespawn = ownerId -> {};

    public void setOnDespawn(Consumer<UUID> onDespawn) {
        this.onDespawn = onDespawn;
    }

    public void register(UUID ownerId, Entity entity) {
        despawn(ownerId);
        activeEntities.put(ownerId, entity);
    }

    public void despawn(UUID ownerId) {
        Entity existing = activeEntities.remove(ownerId);
        if (existing != null) {
            onDespawn.accept(ownerId);
            if (!existing.isDead()) {
                existing.remove();
            }
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
        for (UUID ownerId : Set.copyOf(activeEntities.keySet())) {
            despawn(ownerId);
        }
    }
}
