package rpg.gathering.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Bobber wait time and catch XP ({@code fishing.yml: catch-time}/{@code xp-gain-per-catch}).
 * Wait time shrinks as the fisherman's own level rises, floored so it never reaches (near)
 * 0, so a higher-level fisherman catches noticeably faster than a fresh one.
 */
public final class FishingConfig {

    private int baseMinTicks = 100;
    private int baseMaxTicks = 600;
    private int ticksReductionPerLevel = 4;
    private int minTicksFloor = 20;
    private long xpGainPerCatch = 10;

    public void load(YamlConfiguration config) {
        this.baseMinTicks = config.getInt("catch-time.base-min-ticks", 100);
        this.baseMaxTicks = config.getInt("catch-time.base-max-ticks", 600);
        this.ticksReductionPerLevel = config.getInt("catch-time.ticks-reduction-per-level", 4);
        this.minTicksFloor = config.getInt("catch-time.min-ticks-floor", 20);
        this.xpGainPerCatch = config.getLong("xp-gain-per-catch", 10);
    }

    /** Minimum bobber wait ticks for a fisherman at {@code level}, floored at {@code minTicksFloor}. */
    public int minWaitTicks(int level) {
        return Math.max(minTicksFloor, baseMinTicks - ticksReductionPerLevel * Math.max(0, level - 1));
    }

    /** Maximum bobber wait ticks for a fisherman at {@code level}; never below {@link #minWaitTicks(int)}. */
    public int maxWaitTicks(int level) {
        int reduced = Math.max(minTicksFloor, baseMaxTicks - ticksReductionPerLevel * Math.max(0, level - 1));
        return Math.max(reduced, minWaitTicks(level));
    }

    public long getXpGainPerCatch() {
        return xpGainPerCatch;
    }
}
