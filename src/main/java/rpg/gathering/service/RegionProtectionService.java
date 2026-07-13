package rpg.gathering.service;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Best-effort WorldGuard integration via reflection so orelia-core carries no compile-time
 * dependency on WorldGuard's jar/API (this build environment cannot reach WorldGuard's
 * Maven repo, and a soft-dependency shouldn't force every downstream build to reach it
 * either). If WorldGuard isn't installed, or its API doesn't match what we expect, every
 * check just allows the action - gathering/farming then behaves as if no region protection
 * plugin exists, same as vanilla.
 */
public final class RegionProtectionService {

    private final Object worldGuardPlugin;
    private final Method canBuildMethod;

    public RegionProtectionService(Plugin plugin) {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        Object pluginInstance = null;
        Method method = null;
        if (worldGuard != null && worldGuard.isEnabled()) {
            try {
                Class<?> worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
                pluginInstance = worldGuardPluginClass.cast(worldGuard);
                method = worldGuardPluginClass.getMethod("canBuild", Player.class, Block.class);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.WARNING,
                        "WorldGuard is installed but its API did not match the expected shape; "
                                + "region protection will be skipped for gathering/farming.", e);
                pluginInstance = null;
                method = null;
            }
        } else {
            plugin.getLogger().info("WorldGuard not found; gathering/farming will not respect region protection.");
        }
        this.worldGuardPlugin = pluginInstance;
        this.canBuildMethod = method;
    }

    /** Whether {@code player} is allowed (by WorldGuard, if present) to break/place at {@code block}. */
    public boolean canModify(Player player, Block block) {
        if (canBuildMethod == null) {
            return true;
        }
        try {
            return (boolean) canBuildMethod.invoke(worldGuardPlugin, player, block);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
