package rpg.dungeon.model;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One live attempt at a dungeon: the party inside it, where each member should be
 * returned to when the run ends, and the encounter's runtime state (spawned enemies still
 * alive, the pending timeout task). Never persisted to a database, so these mutable
 * runtime-only fields are safe to hold directly.
 */
public final class DungeonInstance {

    private final UUID id = UUID.randomUUID();
    private final DungeonData data;
    private final int arenaIndex;
    private final Map<UUID, Location> membersAndReturnLocations = new ConcurrentHashMap<>();
    private final Set<UUID> aliveMonsterIds = ConcurrentHashMap.newKeySet();
    private volatile DungeonInstanceStatus status = DungeonInstanceStatus.ACTIVE;
    private volatile BukkitTask timeoutTask;

    public DungeonInstance(DungeonData data, int arenaIndex) {
        this.data = data;
        this.arenaIndex = arenaIndex;
    }

    public UUID getId() {
        return id;
    }

    public DungeonData getData() {
        return data;
    }

    /** Index into {@link DungeonData#getArenas()} this run was assigned - freed by {@link rpg.dungeon.manager.DungeonInstanceManager#remove}. */
    public int getArenaIndex() {
        return arenaIndex;
    }

    public void addMember(UUID playerId, Location returnLocation) {
        membersAndReturnLocations.put(playerId, returnLocation);
    }

    public Map<UUID, Location> getMembers() {
        return Map.copyOf(membersAndReturnLocations);
    }

    public DungeonInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(DungeonInstanceStatus status) {
        this.status = status;
    }

    public void trackMonster(UUID entityId) {
        aliveMonsterIds.add(entityId);
    }

    /** Returns whether the entity was actually tracked (false if already removed/never tracked). */
    public boolean untrackMonster(UUID entityId) {
        return aliveMonsterIds.remove(entityId);
    }

    public boolean isCleared() {
        return aliveMonsterIds.isEmpty();
    }

    public Set<UUID> getAliveMonsterIds() {
        return Set.copyOf(aliveMonsterIds);
    }

    public void setTimeoutTask(BukkitTask task) {
        this.timeoutTask = task;
    }

    public void cancelTimeoutTask() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
    }
}
