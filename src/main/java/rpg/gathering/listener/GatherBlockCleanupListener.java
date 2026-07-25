package rpg.gathering.listener;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import rpg.gathering.repository.GatheringDefinitionRepository;
import rpg.gathering.service.PlacedBlockTrackingService;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps {@link PlacedBlockTrackingService}'s hand-placed tracking in step with the ways a
 * tracked block can leave its coordinate *without* firing a {@code BlockBreakEvent} - fire,
 * explosions, and pistons. {@code GatherBlockBreakListener} is the only other place tracking
 * is cleared, and it only ever sees player breaks, so without this listener a tracked log
 * destroyed by a creeper or burned away would leave its coordinate marked forever: the row
 * never goes away (the table only ever grows) and a natural log that later occupies that exact
 * coordinate would be wrongly excluded from regen.
 *
 * <p>Pistons *move* a block rather than destroying it, so the tracking moves with it (cleared
 * at the origin, re-marked at the destination) instead of being dropped - otherwise a
 * piston-based build would silently lose its regen exemption. Origins are collected and
 * cleared before any destination is marked, since one moved block's destination can be
 * another's origin.
 */
public final class GatherBlockCleanupListener implements Listener {

    private final GatheringDefinitionRepository definitions;
    private final PlacedBlockTrackingService trackingService;

    public GatherBlockCleanupListener(GatheringDefinitionRepository definitions, PlacedBlockTrackingService trackingService) {
        this.definitions = definitions;
        this.trackingService = trackingService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        clearIfTracked(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::clearIfTracked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::clearIfTracked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        moveTracking(event.getBlocks(), event.getDirection());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        moveTracking(event.getBlocks(), event.getDirection());
    }

    /**
     * The gather-block type check runs first so an explosion levelling hundreds of blocks does
     * a cheap {@code Material} map lookup per block instead of building a coordinate key and
     * probing the tracking set for every one of them.
     */
    private void clearIfTracked(Block block) {
        if (!definitions.getGatherBlocks().containsKey(block.getType())) {
            return;
        }
        trackingService.clearPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private void moveTracking(List<Block> blocks, BlockFace direction) {
        List<Block> tracked = new ArrayList<>();
        for (Block block : blocks) {
            if (!definitions.getGatherBlocks().containsKey(block.getType())) {
                continue;
            }
            if (trackingService.isPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ())) {
                tracked.add(block);
            }
        }
        if (tracked.isEmpty()) {
            return;
        }
        for (Block block : tracked) {
            trackingService.clearPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }
        for (Block block : tracked) {
            Block destination = block.getRelative(direction);
            trackingService.markPlaced(destination.getWorld(), destination.getX(), destination.getY(), destination.getZ());
        }
    }
}
