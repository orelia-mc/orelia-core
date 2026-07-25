package rpg.region.service;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Best-effort WorldGuard integration via reflection - same rationale as
 * {@link rpg.gathering.service.RegionProtectionService}: this build environment cannot reach
 * WorldGuard's Maven repo, so orelia-core carries no compile-time dependency on its jar/API.
 * Where {@code RegionProtectionService} only asks "can this player build here",
 * {@link #getRegionIds(Location)} answers the more general "which WorldGuard region IDs apply
 * at this location" - the shared building block both {@code rpg.town} (town detection) and
 * fishing's per-area loot table need. If WorldGuard isn't installed, or its API doesn't match
 * what's expected, every query just returns an empty list (fail-open: no known regions, same
 * as if no region plugin exists).
 */
public final class RegionQueryService {

    private final Object worldGuardPlugin;
    private final Method getRegionManagerMethod;
    private final Method blockVector3AtMethod;
    private final Method getApplicableRegionsMethod;
    private final Method getRegionsMethod;
    private final Method getIdMethod;
    private final Method getPriorityMethod;

    public RegionQueryService(Plugin plugin) {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        Object pluginInstance = null;
        Method regionManagerMethod = null;
        Method vector3AtMethod = null;
        Method applicableRegionsMethod = null;
        Method regionsMethod = null;
        Method idMethod = null;
        Method priorityMethod = null;
        if (worldGuard != null && worldGuard.isEnabled()) {
            try {
                Class<?> worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
                pluginInstance = worldGuardPluginClass.cast(worldGuard);
                regionManagerMethod = worldGuardPluginClass.getMethod("getRegionManager", World.class);

                Class<?> blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
                vector3AtMethod = blockVector3Class.getMethod("at", double.class, double.class, double.class);

                Class<?> regionManagerClass = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
                applicableRegionsMethod = regionManagerClass.getMethod("getApplicableRegions", blockVector3Class);

                Class<?> applicableRegionSetClass = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet");
                regionsMethod = applicableRegionSetClass.getMethod("getRegions");

                Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
                idMethod = protectedRegionClass.getMethod("getId");
                priorityMethod = protectedRegionClass.getMethod("getPriority");
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.WARNING,
                        "WorldGuard is installed but its API did not match the expected shape; "
                                + "region-based detection (town/fishing area) will be skipped.", e);
                pluginInstance = null;
                regionManagerMethod = null;
                vector3AtMethod = null;
                applicableRegionsMethod = null;
                regionsMethod = null;
                idMethod = null;
                priorityMethod = null;
            }
        } else {
            plugin.getLogger().info("WorldGuard not found; region-based detection (town/fishing area) will be skipped.");
        }
        this.worldGuardPlugin = pluginInstance;
        this.getRegionManagerMethod = regionManagerMethod;
        this.blockVector3AtMethod = vector3AtMethod;
        this.getApplicableRegionsMethod = applicableRegionsMethod;
        this.getRegionsMethod = regionsMethod;
        this.getIdMethod = idMethod;
        this.getPriorityMethod = priorityMethod;
    }

    /**
     * WorldGuard region IDs applicable at {@code location}, highest priority first. Empty if
     * WorldGuard isn't installed, its API didn't match, no region applies there, or any
     * reflective call fails.
     */
    public List<String> getRegionIds(Location location) {
        if (getRegionManagerMethod == null) {
            return List.of();
        }
        try {
            Object regionManager = getRegionManagerMethod.invoke(worldGuardPlugin, location.getWorld());
            if (regionManager == null) {
                return List.of();
            }
            Object blockVector3 = blockVector3AtMethod.invoke(null, location.getX(), location.getY(), location.getZ());
            Object applicableRegionSet = getApplicableRegionsMethod.invoke(regionManager, blockVector3);
            Set<?> regions = (Set<?>) getRegionsMethod.invoke(applicableRegionSet);

            List<RegionEntry> entries = new ArrayList<>();
            for (Object region : regions) {
                String id = (String) getIdMethod.invoke(region);
                int priority = (int) getPriorityMethod.invoke(region);
                entries.add(new RegionEntry(id, priority));
            }
            entries.sort(Comparator.comparingInt(RegionEntry::priority).reversed());

            List<String> ids = new ArrayList<>(entries.size());
            for (RegionEntry entry : entries) {
                ids.add(entry.id());
            }
            return Collections.unmodifiableList(ids);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return List.of();
        }
    }

    private record RegionEntry(String id, int priority) {
    }
}
