package rpg.gathering.service;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import rpg.core.scheduler.SchedulerService;
import rpg.gathering.model.PlacedBlockLocation;
import rpg.gathering.repository.PlacedBlockRepository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which gather-block-typed blocks a player placed by hand, so
 * {@code GatherBlockBreakListener} can exclude them from the auto-regen system (a block a
 * player placed for building shouldn't grow back like a natural gathering node). Unlike
 * {@code BlockRegenService}'s pending queue (a full scan every tick regardless of size),
 * {@link #isPlaced} here is checked on every single gather-block break and the set only
 * grows until that exact block is broken again, so a {@link ConcurrentHashMap} key set
 * (O(1) lookup) is used instead of a list.
 */
public final class PlacedBlockTrackingService {

    private final Plugin plugin;
    private final SchedulerService scheduler;
    private final PlacedBlockRepository repository;
    private final Set<PlacedBlockLocation> placed = ConcurrentHashMap.newKeySet();

    public PlacedBlockTrackingService(Plugin plugin, SchedulerService scheduler, PlacedBlockRepository repository) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.repository = repository;
    }

    /** Loads every previously-tracked placed block. Call once during onEnable. */
    public void loadPlaced() {
        placed.addAll(repository.loadAll());
        plugin.getLogger().info("Loaded " + placed.size() + " tracked placed block(s).");
    }

    public void markPlaced(World world, int x, int y, int z) {
        PlacedBlockLocation location = new PlacedBlockLocation(world.getName(), x, y, z);
        if (placed.add(location)) {
            scheduler.runAsync(() -> repository.insert(location));
        }
    }

    public boolean isPlaced(World world, int x, int y, int z) {
        return placed.contains(new PlacedBlockLocation(world.getName(), x, y, z));
    }

    public void clearPlaced(World world, int x, int y, int z) {
        PlacedBlockLocation location = new PlacedBlockLocation(world.getName(), x, y, z);
        if (placed.remove(location)) {
            scheduler.runAsync(() -> repository.delete(location));
        }
    }
}
