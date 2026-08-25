package rpg.extra.mount.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code config.yml: mount.growth} - the tuning knob for how much growth XP a kill grants
 * (the per-level stat curve itself is per-species template data, see {@code mounts.yml}'s
 * {@code growth:} section).
 */
public final class MountGrowthLevelingConfig {

    private long xpPerKill = 5;

    public void load(YamlConfiguration config) {
        this.xpPerKill = config.getLong("mount.growth.xp-per-kill", 5);
    }

    /** Growth XP granted to the player's currently-selected mount for each tagged monster killed while it's summoned. */
    public long getXpPerKill() {
        return xpPerKill;
    }
}
