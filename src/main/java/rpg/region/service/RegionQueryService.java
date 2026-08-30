package rpg.region.service;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Best-effort WorldGuard integration via reflection: this build environment cannot reach
 * WorldGuard's Maven repo, so orelia-core carries no compile-time dependency on its jar/API.
 * Targets the WorldGuard 7 API ({@code WorldGuard.getInstance().getPlatform().getRegionContainer()});
 * {@code WorldGuardPlugin.getRegionManager(World)} was removed when WorldGuard moved off the
 * Bukkit-plugin-as-API-entrypoint shape. {@link #getRegionIds(Location)} answers "which
 * WorldGuard region IDs apply at this location" - the shared building block
 * {@code rpg.town} (town detection), {@code rpg.gathering.service.RegenExclusionService}, and
 * fishing's per-area loot table all need. If WorldGuard isn't installed, or its API doesn't
 * match what's expected, every query just returns an empty list (fail-open: no known regions,
 * same as if no region plugin exists) - startup-time mismatches are logged once from the
 * constructor, and a mismatch first surfacing at query time (WorldGuard reflection succeeded at
 * startup but breaks on an actual call) is logged once from {@link #getRegionIds} the first time
 * it happens, so this never fails open in total silence.
 */
public final class RegionQueryService {

    private final Logger logger;
    /**
     * Set on the first runtime reflection failure inside {@link #getRegionIds}, so that only
     * one WARNING is logged per server session instead of once per call site (block break,
     * fishing catch, spawn check, ...) - a WorldGuard API mismatch here would otherwise either
     * spam the console into uselessness or, if left unlogged, fail open in complete silence,
     * which is exactly the "quietly disabled and nobody notices" risk this exists to close.
     */
    private final AtomicBoolean loggedQueryFailure = new AtomicBoolean(false);
    private final Object regionContainer;
    private final Method adaptWorldMethod;
    private final Method regionManagerForWorldMethod;
    private final Method blockVector3AtMethod;
    private final Method getApplicableRegionsMethod;
    private final Method getRegionsMethod;
    private final Method getIdMethod;
    private final Method getPriorityMethod;
    private final Method getParentMethod;

    public RegionQueryService(Plugin plugin) {
        this.logger = plugin.getLogger();
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        Object container = null;
        Method adaptMethod = null;
        Method regionManagerMethod = null;
        Method vector3AtMethod = null;
        Method applicableRegionsMethod = null;
        Method regionsMethod = null;
        Method idMethod = null;
        Method priorityMethod = null;
        Method parentMethod = null;
        if (worldGuard != null && worldGuard.isEnabled()) {
            try {
                Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object worldGuardInstance = worldGuardClass.getMethod("getInstance").invoke(null);
                Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuardInstance);

                Class<?> platformClass = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform");
                Class<?> regionContainerClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
                container = platformClass.getMethod("getRegionContainer").invoke(platform);

                Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
                Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                adaptMethod = bukkitAdapterClass.getMethod("adapt", World.class);
                regionManagerMethod = regionContainerClass.getMethod("get", weWorldClass);

                Class<?> blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
                vector3AtMethod = blockVector3Class.getMethod("at", double.class, double.class, double.class);

                Class<?> regionManagerClass = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
                applicableRegionsMethod = regionManagerClass.getMethod("getApplicableRegions", blockVector3Class);

                Class<?> applicableRegionSetClass = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet");
                regionsMethod = applicableRegionSetClass.getMethod("getRegions");

                Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
                idMethod = protectedRegionClass.getMethod("getId");
                priorityMethod = protectedRegionClass.getMethod("getPriority");
                parentMethod = protectedRegionClass.getMethod("getParent");
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.WARNING,
                        "WorldGuard is installed but its API did not match the expected shape; "
                                + "region-based detection (town/fishing area) will be skipped.", e);
                container = null;
                adaptMethod = null;
                regionManagerMethod = null;
                vector3AtMethod = null;
                applicableRegionsMethod = null;
                regionsMethod = null;
                idMethod = null;
                priorityMethod = null;
                parentMethod = null;
            }
        } else {
            plugin.getLogger().info("WorldGuard not found; region-based detection (town/fishing area) will be skipped.");
        }
        this.regionContainer = container;
        this.adaptWorldMethod = adaptMethod;
        this.regionManagerForWorldMethod = regionManagerMethod;
        this.blockVector3AtMethod = vector3AtMethod;
        this.getApplicableRegionsMethod = applicableRegionsMethod;
        this.getRegionsMethod = regionsMethod;
        this.getIdMethod = idMethod;
        this.getPriorityMethod = priorityMethod;
        this.getParentMethod = parentMethod;
    }

    /**
     * WorldGuard region IDs applicable at {@code location}, most specific first. Regions in a
     * parent/child relationship are ordered child-before-parent regardless of their raw
     * {@code priority} value - matching WorldGuard's own convention that a child region always
     * takes precedence over its ancestors - and unrelated regions fall back to priority order
     * (highest first), same as before. Empty if WorldGuard isn't installed, its API didn't
     * match, no region applies there, or any reflective call fails.
     */
    public List<String> getRegionIds(Location location) {
        if (regionContainer == null) {
            return List.of();
        }
        try {
            Object weWorld = adaptWorldMethod.invoke(null, location.getWorld());
            Object regionManager = regionManagerForWorldMethod.invoke(regionContainer, weWorld);
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
                entries.add(new RegionEntry(id, priority, resolveParentId(region)));
            }
            return orderByEffectivePriority(entries);
        } catch (ReflectiveOperationException | ClassCastException e) {
            if (loggedQueryFailure.compareAndSet(false, true)) {
                logger.log(Level.WARNING,
                        "WorldGuard region lookup failed at runtime (API shape likely changed "
                                + "after startup detection succeeded); region-based detection "
                                + "(town/fishing area/regen exclusion) will silently return no "
                                + "regions for the rest of this session. Logged once to avoid "
                                + "spamming the console on every gathering/combat tick.", e);
            }
            return List.of();
        }
    }

    /** Best-effort parent region ID; {@code null} if unavailable rather than failing the whole lookup. */
    private String resolveParentId(Object region) {
        if (getParentMethod == null) {
            return null;
        }
        try {
            Object parent = getParentMethod.invoke(region);
            return parent == null ? null : (String) getIdMethod.invoke(parent);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    /**
     * Orders {@code entries} child-before-parent (regardless of {@code priority}), falling back
     * to {@code priority} descending (highest first) for regions with no ancestor/descendant
     * relationship - same as before this method existed. WorldGuard-independent and pure, so
     * it's unit-testable without WorldGuard on the classpath.
     *
     * <p>Implemented as a priority-guided topological sort (Kahn's algorithm) rather than a
     * comparator: a region only becomes eligible once every one of its descendants present in
     * {@code entries} has already been placed, and among eligible regions the highest-priority
     * one goes next. A plain {@code Comparator} mixing "child always beats its ancestors" with
     * "otherwise compare by priority" is not transitive in general (a low-priority child can
     * outrank a high-priority parent, which can in turn outrank an unrelated region that itself
     * outranks the child by priority) and would make {@link List#sort} throw at runtime.
     */
    static List<String> orderByEffectivePriority(List<RegionEntry> entries) {
        Map<String, RegionEntry> byId = new HashMap<>();
        for (RegionEntry entry : entries) {
            byId.put(entry.id(), entry);
        }

        Map<String, Integer> pendingChildren = new HashMap<>();
        for (RegionEntry entry : entries) {
            pendingChildren.put(entry.id(), 0);
        }
        for (RegionEntry entry : entries) {
            String parentId = entry.parentId();
            if (isEligibleParent(parentId, entry.id(), byId)) {
                pendingChildren.merge(parentId, 1, Integer::sum);
            }
        }

        Comparator<RegionEntry> byPriorityThenId =
                Comparator.comparingInt(RegionEntry::priority).reversed().thenComparing(RegionEntry::id);
        PriorityQueue<RegionEntry> ready = new PriorityQueue<>(byPriorityThenId);
        for (RegionEntry entry : entries) {
            if (pendingChildren.get(entry.id()) == 0) {
                ready.add(entry);
            }
        }

        List<String> ordered = new ArrayList<>(entries.size());
        Set<String> emitted = new HashSet<>();
        while (!ready.isEmpty()) {
            RegionEntry next = ready.poll();
            if (!emitted.add(next.id())) {
                continue;
            }
            ordered.add(next.id());
            String parentId = next.parentId();
            if (isEligibleParent(parentId, next.id(), byId) && pendingChildren.merge(parentId, -1, Integer::sum) == 0) {
                ready.add(byId.get(parentId));
            }
        }

        // A malformed parent chain (e.g. a cycle) can leave regions un-emitted above; fall back
        // to plain priority order for those rather than silently dropping region IDs.
        if (ordered.size() < entries.size()) {
            List<RegionEntry> leftover = new ArrayList<>();
            for (RegionEntry entry : entries) {
                if (!emitted.contains(entry.id())) {
                    leftover.add(entry);
                }
            }
            leftover.sort(byPriorityThenId);
            for (RegionEntry entry : leftover) {
                ordered.add(entry.id());
            }
        }

        return Collections.unmodifiableList(ordered);
    }

    private static boolean isEligibleParent(String parentId, String ownId, Map<String, RegionEntry> byId) {
        return parentId != null && !parentId.equals(ownId) && byId.containsKey(parentId);
    }

    record RegionEntry(String id, int priority, String parentId) {
    }
}
