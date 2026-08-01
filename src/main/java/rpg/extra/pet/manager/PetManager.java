package rpg.extra.pet.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each player's currently-spawned pet entity (SOW PetModule). Purely in-memory -
 * spawned pets don't survive a server restart, only the ownership/selection records do.
 */
public final class PetManager {

    private static final double FOLLOW_DISTANCE = 2.5;

    private final Map<UUID, LivingEntity> activeEntities = new ConcurrentHashMap<>();

    public void register(UUID ownerId, LivingEntity entity) {
        despawn(ownerId);
        activeEntities.put(ownerId, entity);
    }

    public void despawn(UUID ownerId) {
        LivingEntity existing = activeEntities.remove(ownerId);
        if (existing != null && !existing.isDead()) {
            existing.remove();
        }
    }

    public boolean hasActivePet(UUID ownerId) {
        LivingEntity entity = activeEntities.get(ownerId);
        return entity != null && !entity.isDead();
    }

    /** Teleports every active pet whose owner is online and too far away back to their owner's side. */
    public void tickFollow() {
        activeEntities.forEach((ownerId, entity) -> {
            if (entity.isDead()) {
                return;
            }
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null) {
                entity.remove();
                activeEntities.remove(ownerId);
                return;
            }
            if (!owner.getWorld().equals(entity.getWorld()) || owner.getLocation().distance(entity.getLocation()) > FOLLOW_DISTANCE) {
                entity.teleport(owner.getLocation());
            }
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
            }
        });
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
