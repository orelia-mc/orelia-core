package rpg.core.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * Thin wrapper around {@link BukkitScheduler} so modules never call {@code Bukkit.getScheduler()}
 * directly. Centralizing this makes a future Folia region-scheduler migration a one-file change.
 */
public final class SchedulerService {

    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public SchedulerService(Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
    }

    public BukkitTask runSync(Runnable task) {
        return scheduler.runTask(plugin, task);
    }

    public BukkitTask runAsync(Runnable task) {
        return scheduler.runTaskAsynchronously(plugin, task);
    }

    public BukkitTask runLater(Runnable task, long delayTicks) {
        return scheduler.runTaskLater(plugin, task, delayTicks);
    }

    public BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks);
    }
}
