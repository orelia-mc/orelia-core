package rpg.gathering.service;

import org.bukkit.Location;
import rpg.gathering.config.RegenExclusionConfig;
import rpg.region.service.RegionQueryService;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Answers "should gathering be disabled at this location" by checking whether any WorldGuard
 * region ID applicable there is listed in {@code gathering.yml: regen-exclusion.regions}.
 * Built on {@link RegionQueryService} rather than talking to WorldGuard directly, so it stays
 * fail-open (no WorldGuard installed, or exclusion disabled, means nothing is ever excluded)
 * without repeating the reflection setup - same shape as
 * {@code rpg.town.service.TownDetectionService}.
 *
 * <p>This replaced a per-coordinate "which blocks did a player place by hand" tracking table.
 * Region membership is a property of the *place*, not of how a block got there, which is what
 * makes it both simpler and more correct: it covers naturally-grown trees left standing inside
 * a build, WorldEdit pastes and schematics (which never fire {@code BlockPlaceEvent}), and
 * blocks destroyed by fire/explosions/pistons - all cases the coordinate tracking either
 * missed outright or needed dedicated listeners and a growing database table to keep in step.
 *
 * <p>The tradeoff is granularity and a harder reliance on WorldGuard: a build outside every
 * listed region is not protected, and if WorldGuard fails to load, the fail-open contract
 * means nothing is excluded until it is back.
 */
public final class RegenExclusionService {

    private final RegionQueryService regionQueryService;
    private final RegenExclusionConfig config;

    public RegenExclusionService(RegionQueryService regionQueryService, RegenExclusionConfig config) {
        this.regionQueryService = regionQueryService;
        this.config = config;
    }

    public boolean isExcluded(Location location) {
        if (!config.isEnabled()) {
            return false;
        }
        return matchesExcludedRegion(regionQueryService.getRegionIds(location), config.getExcludedRegions());
    }

    /** Pure (no Bukkit/WorldGuard dependency) so the matching rule itself is unit-testable. */
    static boolean matchesExcludedRegion(Collection<String> regionIds, Set<String> excludedRegions) {
        if (excludedRegions.isEmpty()) {
            return false;
        }
        for (String regionId : regionIds) {
            if (excludedRegions.contains(regionId.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
