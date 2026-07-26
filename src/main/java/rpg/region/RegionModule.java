package rpg.region;

import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.region.service.RegionQueryService;

/**
 * Owns {@link RegionQueryService}, the shared WorldGuard-region lookup used by both town
 * detection ({@code rpg.town}) and fishing's per-area loot table ({@code rpg.gathering}).
 * Registered right after {@link rpg.database.DatabaseModule}, since {@code GatheringModule}
 * (which needs it) registers early - this module has no dependency of its own, only a
 * runtime lookup of the WorldGuard plugin via Bukkit.
 */
public final class RegionModule implements RpgModule {

    private RegionQueryService queryService;

    @Override
    public String getName() {
        return "region";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.queryService = new RegionQueryService(plugin);
    }

    @Override
    public void onDisable() {
    }

    public RegionQueryService getQueryService() {
        return queryService;
    }
}
