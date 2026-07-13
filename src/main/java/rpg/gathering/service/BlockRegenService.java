package rpg.gathering.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import rpg.core.scheduler.SchedulerService;
import rpg.gathering.model.BlockRegenTask;
import rpg.gathering.repository.BlockRegenRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Tracks blocks waiting to regenerate after being gathered (SOW 3.1). Pending tasks are
 * persisted immediately on break so a crash/restart just resumes the same queue.
 *
 * <p>The regen tick itself runs on the main thread (not async): it both reads chunk-load
 * state and writes block types, and both are main-thread-only Bukkit operations. Only the
 * repository read/write around it is dispatched async, per SOW 4.1.
 */
public final class BlockRegenService {

    private final Plugin plugin;
    private final SchedulerService scheduler;
    private final BlockRegenRepository repository;
    private final List<BlockRegenTask> pending = new CopyOnWriteArrayList<>();

    public BlockRegenService(Plugin plugin, SchedulerService scheduler, BlockRegenRepository repository) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.repository = repository;
    }

    /** Loads any regen tasks left over from before a restart/crash. Call once during onEnable. */
    public void loadPending() {
        pending.addAll(repository.loadAll());
        plugin.getLogger().info("Loaded " + pending.size() + " pending block regen task(s).");
    }

    public void start(long periodTicks) {
        scheduler.runTimer(this::tick, periodTicks, periodTicks);
    }

    /**
     * Replaces the block at the given coordinate with its waiting block and queues it to
     * come back after {@code cooldownSeconds}. Must be called on the main thread, and only
     * once the block has actually been removed - see {@link #scheduleNextTick} for the
     * event-triggered break, where vanilla removes the block after the listener returns.
     */
    public void schedule(World world, int x, int y, int z, Material originalMaterial, Material waitingMaterial, int cooldownSeconds) {
        world.getBlockAt(x, y, z).setType(waitingMaterial, false);
        BlockRegenTask task = new BlockRegenTask(UUID.randomUUID(), world.getName(), x, y, z,
                originalMaterial.name(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        pending.add(task);
        scheduler.runAsync(() -> repository.save(task));
    }

    /**
     * Same as {@link #schedule}, deferred to the next tick. Use this from a
     * {@code BlockBreakEvent} handler that does not cancel the event: Bukkit only removes
     * the block and spawns drops after every listener returns, so overwriting the block
     * inside the handler itself would race that removal (drops silently vanish, or the
     * server tries to "break" the waiting block instead of the original one).
     */
    public void scheduleNextTick(World world, int x, int y, int z, Material originalMaterial, Material waitingMaterial, int cooldownSeconds) {
        scheduler.runSync(() -> schedule(world, x, y, z, originalMaterial, waitingMaterial, cooldownSeconds));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (BlockRegenTask task : pending) {
            if (task.restoreAtMillis() > now) {
                continue;
            }
            restoreIfLoaded(task);
        }
    }

    /** Called by the chunk-load listener so a regen due while its chunk was unloaded applies immediately on load. */
    public void onChunkLoaded(String world, int chunkX, int chunkZ) {
        long now = System.currentTimeMillis();
        for (BlockRegenTask task : pending) {
            if (!task.world().equals(world) || task.restoreAtMillis() > now) {
                continue;
            }
            if ((task.x() >> 4) == chunkX && (task.z() >> 4) == chunkZ) {
                restore(task);
            }
        }
    }

    private void restoreIfLoaded(BlockRegenTask task) {
        World world = Bukkit.getWorld(task.world());
        if (world == null) {
            drop(task);
            return;
        }
        if (!world.isChunkLoaded(task.x() >> 4, task.z() >> 4)) {
            return;
        }
        restore(task);
    }

    private void restore(BlockRegenTask task) {
        World world = Bukkit.getWorld(task.world());
        if (world == null) {
            drop(task);
            return;
        }
        world.getBlockAt(task.x(), task.y(), task.z()).setType(parseMaterial(task.originalMaterial()), false);
        drop(task);
    }

    private void drop(BlockRegenTask task) {
        pending.remove(task);
        scheduler.runAsync(() -> repository.delete(task.id()));
    }

    private Material parseMaterial(String raw) {
        try {
            return Material.valueOf(raw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Unknown material in pending block regen task: " + raw);
            return Material.STONE;
        }
    }
}
