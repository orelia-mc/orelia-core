package rpg.monster.spawnpoint.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import rpg.monster.repository.MonsterRepository;
import rpg.monster.service.MonsterAbilityCastService;
import rpg.monster.service.MonsterSpawnService;
import rpg.monster.spawnpoint.manager.MonsterSpawnPointManager;
import rpg.monster.spawnpoint.model.MonsterSpawnPoint;
import rpg.monster.spawnpoint.repository.MonsterSpawnPointRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers/removes admin-placed {@link MonsterSpawnPoint}s and periodically spawns at
 * each one (SOW follow-up: replaces one-shot {@code /oladmin spawn} for recurring farm
 * spots - a player stands where they want monsters, runs the add command, and that
 * location keeps spawning up to {@code maxAlive} of that monster over time).
 */
public final class MonsterSpawnPointService {

    private final MonsterSpawnPointRepository repository;
    private final MonsterSpawnPointManager manager;
    private final MonsterSpawnService spawnService;
    private final MonsterRepository monsterRepository;
    private final MonsterAbilityCastService abilityCastService;

    public MonsterSpawnPointService(MonsterSpawnPointRepository repository, MonsterSpawnPointManager manager,
                                     MonsterSpawnService spawnService, MonsterRepository monsterRepository,
                                     MonsterAbilityCastService abilityCastService) {
        this.repository = repository;
        this.manager = manager;
        this.spawnService = spawnService;
        this.monsterRepository = monsterRepository;
        this.abilityCastService = abilityCastService;
    }

    public void loadAll() {
        manager.loadAll(repository.loadAll());
    }

    /** Empty if {@code monsterId} doesn't exist in monsters.yml. {@code targetLevel} scales spawned monsters' hp/attack/defense - null means no scaling. */
    public Optional<MonsterSpawnPoint> add(Player admin, String monsterId, int intervalSeconds, int maxAlive, Integer targetLevel) {
        if (monsterRepository.findById(monsterId).isEmpty()) {
            return Optional.empty();
        }
        Location location = admin.getLocation();
        MonsterSpawnPoint point = new MonsterSpawnPoint(UUID.randomUUID(), monsterId, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), intervalSeconds, maxAlive, targetLevel);
        manager.add(point);
        repository.save(point);
        return Optional.of(point);
    }

    public boolean remove(UUID id) {
        boolean removed = manager.remove(id);
        if (removed) {
            repository.delete(id);
        }
        return removed;
    }

    public Map<UUID, MonsterSpawnPoint> getAll() {
        return manager.getAll();
    }

    /** Call periodically (e.g. every 20 ticks); spawns at every point that is due and under its alive cap. */
    public void tick() {
        for (MonsterSpawnPoint point : manager.getAll().values()) {
            if (manager.aliveCount(point.getId()) >= point.getMaxAlive()) {
                continue;
            }
            if (!manager.isDueToSpawn(point)) {
                continue;
            }
            World world = Bukkit.getWorld(point.getWorld());
            if (world == null) {
                continue;
            }
            Location location = new Location(world, point.getX(), point.getY(), point.getZ());
            spawnService.spawn(point.getMonsterId(), location, point.getId(), point.getTargetLevel())
                    .ifPresent(entity -> {
                        manager.onSpawned(point.getId(), entity.getUniqueId());
                        monsterRepository.findById(point.getMonsterId())
                                .ifPresent(data -> abilityCastService.registerIfAble(entity, data));
                    });
        }
    }

    /** Call when a spawned entity dies/despawns so its spawn point's alive count frees up. */
    public void onEntityRemoved(LivingEntity entity) {
        spawnService.spawnPointIdOf(entity).ifPresent(pointId -> manager.onEntityRemoved(pointId, entity.getUniqueId()));
    }
}
