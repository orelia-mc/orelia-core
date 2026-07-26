package rpg.gathering.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import rpg.core.scheduler.SchedulerService;
import rpg.gathering.model.BlockRegenTask;
import rpg.gathering.model.GatherBlockTemplate;
import rpg.gathering.repository.BlockRegenRepository;
import rpg.gathering.repository.GatheringDefinitionRepository;

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
    private final GatheringDefinitionRepository definitions;
    private final RegenExclusionService exclusionService;
    private final List<BlockRegenTask> pending = new CopyOnWriteArrayList<>();

    public BlockRegenService(Plugin plugin, SchedulerService scheduler, BlockRegenRepository repository,
                              GatheringDefinitionRepository definitions, RegenExclusionService exclusionService) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.repository = repository;
        this.definitions = definitions;
        this.exclusionService = exclusionService;
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

    /**
     * Cancels any pending regen task at the given coordinate, if one exists. A coordinate can
     * carry a task queued before the area became excluded - a natural node was harvested
     * (scheduling a restore), and the spot only later ended up inside a
     * {@code gathering.yml: regen-exclusion.regions} region. Without cancelling it, that
     * leftover task fires regardless once the cooldown elapses.
     */
    public void cancelPending(World world, int x, int y, int z) {
        pending.removeIf(task -> {
            boolean match = task.world().equals(world.getName()) && task.x() == x && task.y() == y && task.z() == z;
            if (match) {
                scheduler.runAsync(() -> repository.delete(task.id()));
            }
            return match;
        });
    }

    /**
     * Wipes every pending regen task, in-memory and persisted. Admin escape hatch
     * ({@code /oladmin gathering resetregen}) for clearing out stale tasks - e.g. ones
     * queued before {@link #cancelPending} existed, back when a hand-placed block over a
     * waiting node had no way to cancel the node's leftover restore.
     *
     * @return how many tasks were cleared
     */
    public int clearAll() {
        int count = pending.size();
        pending.clear();
        scheduler.runAsync(repository::deleteAll);
        return count;
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
        Block block = world.getBlockAt(task.x(), task.y(), task.z());
        Material original = parseMaterial(task.originalMaterial());
        // Checked here as well as on break so that defining a new exclusion region (plus
        // /oladmin reload) retroactively stops regens already queued inside it, instead of
        // leaving them to fire one last time after the region exists.
        if (exclusionService.isExcluded(block.getLocation()) || !isRestorable(block.getType(), original)) {
            drop(task);
            return;
        }
        block.setType(original, false);
        drop(task);
    }

    /**
     * Whether the coordinate is still "ours" to restore. Region exclusion only covers areas an
     * admin has drawn a region around, so this is the WorldGuard-independent backstop for a
     * player who breaks the waiting block and builds something else (plain planks, stone, a
     * WorldEdit paste, another plugin) over a spot with a task still queued - restoring
     * unconditionally would overwrite whatever they put there once the cooldown elapsed.
     *
     * <p>Restoring is allowed only while the coordinate still holds the waiting block this
     * service put there, the original material already, or air (the waiting block was broken
     * but nothing was built in its place - a node coming back into an empty spot is the
     * intended behavior). Anything else means someone claimed the coordinate, so the task is
     * dropped instead. The tradeoff is that a node permanently built over stops regenerating,
     * for ore as much as for logs.
     */
    private boolean isRestorable(Material current, Material original) {
        if (current == Material.AIR || current == original) {
            return true;
        }
        GatherBlockTemplate template = definitions.getGatherBlocks().get(original);
        return template != null && current == template.replaceBlock();
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
