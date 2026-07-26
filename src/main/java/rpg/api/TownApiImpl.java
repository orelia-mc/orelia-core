package rpg.api;

import org.bukkit.Location;
import rpg.town.service.TownDetectionService;

final class TownApiImpl implements TownApi {

    private final TownDetectionService detectionService;

    TownApiImpl(TownDetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @Override
    public boolean isInTown(Location location) {
        return detectionService.isInTown(location);
    }
}
