package rpg.extra.pet.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks each player's currently-spawned pet entity (SOW PetModule). Purely in-memory -
 * spawned pets don't survive a server restart, only the ownership/selection records do.
 */
public final class PetManager {

    private static final double FOLLOW_DISTANCE = 2.5;

    private final Map<UUID, LivingEntity> activeEntities = new ConcurrentHashMap<>();
    /**
     * Called at the top of every {@link #despawn} (including from {@link #tickFollow}'s
     * owner-offline cleanup and {@link #despawnAll}), so anything hooked onto "this player's
     * pet is no longer active" - e.g. {@code PetGrowthService#clearGrowthBonus} - fires through
     * every despawn path (explicit {@code /ol pet dismiss}, owner going offline, server
     * shutdown) rather than only the one a caller happens to remember to call it from.
     */
    private Consumer<UUID> onDespawn = ownerId -> {};

    public void setOnDespawn(Consumer<UUID> onDespawn) {
        this.onDespawn = onDespawn;
    }

    public void register(UUID ownerId, LivingEntity entity) {
        despawn(ownerId);
        activeEntities.put(ownerId, entity);
    }

    public void despawn(UUID ownerId) {
        LivingEntity existing = activeEntities.remove(ownerId);
        if (existing != null) {
            onDespawn.accept(ownerId);
            if (!existing.isDead()) {
                existing.remove();
            }
        }
    }

    public boolean hasActivePet(UUID ownerId) {
        LivingEntity entity = activeEntities.get(ownerId);
        return entity != null && !entity.isDead();
    }

    /** Teleports every active pet whose owner is online and too far away back to their owner's side. */
    public void tickFollow() {
        // Snapshot the key set first - despawn() mutates activeEntities, and doing that from
        // inside a ConcurrentHashMap#forEach lambda is safe but confusing to reason about.
        for (UUID ownerId : Set.copyOf(activeEntities.keySet())) {
            LivingEntity entity = activeEntities.get(ownerId);
            if (entity == null || entity.isDead()) {
                continue;
            }
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null) {
                despawn(ownerId);
                continue;
            }
            if (!owner.getWorld().equals(entity.getWorld()) || owner.getLocation().distance(entity.getLocation()) > FOLLOW_DISTANCE) {
                entity.teleport(owner.getLocation());
            }
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
    }

    public void despawnAll() {
        for (UUID ownerId : Set.copyOf(activeEntities.keySet())) {
            despawn(ownerId);
        }
    }
}
