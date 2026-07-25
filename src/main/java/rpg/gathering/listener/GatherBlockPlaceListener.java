package rpg.gathering.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.model.GatherBlockTemplate;
import rpg.gathering.repository.GatheringDefinitionRepository;
import rpg.gathering.service.BlockRegenService;
import rpg.gathering.service.PlacedBlockTrackingService;

/**
 * Marks woodcutting-typed blocks a player places by hand (e.g. a building log) so
 * {@code GatherBlockBreakListener} can exclude them from the auto-regen system. WorldEdit-style
 * bulk pastes don't fire this event per block, so decorative structures built that way are
 * unaffected and still behave like natural gathering nodes.
 *
 * <p>Mining (ore) blocks are deliberately excluded from this tracking - ore should keep
 * regenerating unconditionally regardless of how it was placed, unlike logs.
 *
 * <p>Also cancels any regen task already pending at this coordinate. A spot can carry a
 * leftover task from before this placement - e.g. a natural log was harvested (scheduling a
 * restore after its cooldown) and a player then hand-placed their own log over the waiting
 * spot - without cancelling it, that stale task would still fire later and force the
 * coordinate back to a log, making a manually placed block look like it "regenerated".
 */
public final class GatherBlockPlaceListener implements Listener {

    private final GatheringDefinitionRepository definitions;
    private final PlacedBlockTrackingService trackingService;
    private final BlockRegenService regenService;

    public GatherBlockPlaceListener(GatheringDefinitionRepository definitions, PlacedBlockTrackingService trackingService,
                                     BlockRegenService regenService) {
        this.definitions = definitions;
        this.trackingService = trackingService;
        this.regenService = regenService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        GatherBlockTemplate template = definitions.getGatherBlocks().get(block.getType());
        if (template == null || template.actionType() != GatherActionType.WOODCUTTING) {
            return;
        }
        trackingService.markPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ());
        regenService.cancelPending(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
