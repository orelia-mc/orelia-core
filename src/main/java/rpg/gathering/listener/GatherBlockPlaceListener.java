package rpg.gathering.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import rpg.gathering.repository.GatheringDefinitionRepository;
import rpg.gathering.service.PlacedBlockTrackingService;

/**
 * Marks gather-block-typed blocks a player places by hand (e.g. a building log) so
 * {@code GatherBlockBreakListener} can exclude them from the auto-regen system. WorldEdit-style
 * bulk pastes don't fire this event per block, so decorative structures built that way are
 * unaffected and still behave like natural gathering nodes.
 */
public final class GatherBlockPlaceListener implements Listener {

    private final GatheringDefinitionRepository definitions;
    private final PlacedBlockTrackingService trackingService;

    public GatherBlockPlaceListener(GatheringDefinitionRepository definitions, PlacedBlockTrackingService trackingService) {
        this.definitions = definitions;
        this.trackingService = trackingService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!definitions.getGatherBlocks().containsKey(block.getType())) {
            return;
        }
        trackingService.markPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
