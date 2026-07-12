package rpg.monster.spawnpoint.manager;

import rpg.monster.spawnpoint.model.MonsterSpawnPoint;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of every {@link MonsterSpawnPoint}, plus runtime bookkeeping (which
 * spawned entities are still alive per point, and when each point last spawned) that isn't
 * persisted - only the point definitions themselves survive a restart.
 */
public final class MonsterSpawnPointManager {

    private final Map<UUID, MonsterSpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> aliveEntitiesByPoint = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSpawnAtMillis = new ConcurrentHashMap<>();

    public void loadAll(Iterable<MonsterSpawnPoint> loaded) {
        points.clear();
        loaded.forEach(point -> points.put(point.getId(), point));
    }

    public void add(MonsterSpawnPoint point) {
        points.put(point.getId(), point);
    }

    public boolean remove(UUID id) {
        aliveEntitiesByPoint.remove(id);
        lastSpawnAtMillis.remove(id);
        return points.remove(id) != null;
    }

    public Map<UUID, MonsterSpawnPoint> getAll() {
        return Map.copyOf(points);
    }

    public int aliveCount(UUID pointId) {
        return aliveEntitiesByPoint.getOrDefault(pointId, Set.of()).size();
    }

    public void onSpawned(UUID pointId, UUID entityId) {
        aliveEntitiesByPoint.computeIfAbsent(pointId, id -> new HashSet<>()).add(entityId);
        lastSpawnAtMillis.put(pointId, System.currentTimeMillis());
    }

    public void onEntityRemoved(UUID pointId, UUID entityId) {
        Set<UUID> alive = aliveEntitiesByPoint.get(pointId);
        if (alive != null) {
            alive.remove(entityId);
        }
    }

    public boolean isDueToSpawn(MonsterSpawnPoint point) {
        long last = lastSpawnAtMillis.getOrDefault(point.getId(), 0L);
        return System.currentTimeMillis() - last >= point.getIntervalSeconds() * 1000L;
    }
}
