package rpg.gathering.service;

import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Best-effort WorldGuard integration via reflection so orelia-core carries no compile-time
 * dependency on WorldGuard's jar/API (this build environment cannot reach WorldGuard's
 * Maven repo, and a soft-dependency shouldn't force every downstream build to reach it
 * either). Targets the WorldGuard 7 region-query API
 * ({@code WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().testBuild(...)});
 * {@code WorldGuardPlugin.canBuild(Player, Block)} was removed along with
 * {@code getRegionManager(World)} (see {@link rpg.region.service.RegionQueryService}). If
 * WorldGuard isn't installed, or its API doesn't match what we expect, every check just allows
 * the action - gathering/farming then behaves as if no region protection plugin exists, same as
 * vanilla.
 */
public final class RegionProtectionService {

    private final Object worldGuardPlugin;
    private final Object regionQuery;
    private final Method wrapPlayerMethod;
    private final Method adaptLocationMethod;
    private final Method testBuildMethod;
    private final Object buildFlagArray;

    public RegionProtectionService(Plugin plugin) {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        Object pluginInstance = null;
        Object query = null;
        Method wrapPlayer = null;
        Method adaptLocation = null;
        Method testBuild = null;
        Object flagArray = null;
        if (worldGuard != null && worldGuard.isEnabled()) {
            try {
                Class<?> worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
                pluginInstance = worldGuardPluginClass.cast(worldGuard);
                wrapPlayer = worldGuardPluginClass.getMethod("wrapPlayer", Player.class);

                Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object worldGuardInstance = worldGuardClass.getMethod("getInstance").invoke(null);
                Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuardInstance);

                Class<?> platformClass = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform");
                Object container = platformClass.getMethod("getRegionContainer").invoke(platform);

                Class<?> regionContainerClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
                query = regionContainerClass.getMethod("createQuery").invoke(container);

                Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                adaptLocation = bukkitAdapterClass.getMethod("adapt", org.bukkit.Location.class);

                Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
                Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                Object buildFlag = flagsClass.getField("BUILD").get(null);
                flagArray = Array.newInstance(stateFlagClass, 1);
                Array.set(flagArray, 0, buildFlag);

                Class<?> localPlayerClass = Class.forName("com.sk89q.worldguard.LocalPlayer");
                Class<?> weLocationClass = Class.forName("com.sk89q.worldedit.util.Location");
                Class<?> regionQueryClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
                testBuild = regionQueryClass.getMethod("testBuild", weLocationClass, localPlayerClass, flagArray.getClass());
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.WARNING,
                        "WorldGuard is installed but its API did not match the expected shape; "
                                + "region protection will be skipped for gathering/farming.", e);
                pluginInstance = null;
                query = null;
                wrapPlayer = null;
                adaptLocation = null;
                testBuild = null;
                flagArray = null;
            }
        } else {
            plugin.getLogger().info("WorldGuard not found; gathering/farming will not respect region protection.");
        }
        this.worldGuardPlugin = pluginInstance;
        this.regionQuery = query;
        this.wrapPlayerMethod = wrapPlayer;
        this.adaptLocationMethod = adaptLocation;
        this.testBuildMethod = testBuild;
        this.buildFlagArray = flagArray;
    }

    /** Whether {@code player} is allowed (by WorldGuard, if present) to break/place at {@code block}. */
    public boolean canModify(Player player, Block block) {
        if (regionQuery == null) {
            return true;
        }
        try {
            Object localPlayer = wrapPlayerMethod.invoke(worldGuardPlugin, player);
            Object weLocation = adaptLocationMethod.invoke(null, block.getLocation());
            return (boolean) testBuildMethod.invoke(regionQuery, weLocation, localPlayer, buildFlagArray);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
