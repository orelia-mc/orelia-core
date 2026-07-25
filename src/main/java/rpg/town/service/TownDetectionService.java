package rpg.town.service;

import org.bukkit.Location;
import rpg.region.service.RegionQueryService;
import rpg.town.config.TownConfig;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Answers "is this location inside a town" by checking whether any WorldGuard region ID
 * applicable there is listed in {@code config.yml: town-detection.town-regions}
 * ({@link TownConfig}). Built on {@link RegionQueryService} rather than talking to WorldGuard
 * directly, so it stays fail-open (no WorldGuard installed, or detection disabled, means never
 * "in town") without repeating the reflection setup.
 */
public final class TownDetectionService {

    private final RegionQueryService regionQueryService;
    private final TownConfig config;

    public TownDetectionService(RegionQueryService regionQueryService, TownConfig config) {
        this.regionQueryService = regionQueryService;
        this.config = config;
    }

    public boolean isInTown(Location location) {
        if (!config.isEnabled()) {
            return false;
        }
        return matchesTownRegion(regionQueryService.getRegionIds(location), config.getTownRegions());
    }

    /** Pure (no Bukkit/WorldGuard dependency) so the matching rule itself is unit-testable. */
    static boolean matchesTownRegion(Collection<String> regionIds, Set<String> townRegions) {
        if (townRegions.isEmpty()) {
            return false;
        }
        for (String regionId : regionIds) {
            if (townRegions.contains(regionId.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
