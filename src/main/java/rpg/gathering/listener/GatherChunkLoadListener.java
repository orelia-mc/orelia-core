package rpg.gathering.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import rpg.gathering.service.BlockRegenService;

/** Restores any block regen task that came due while its chunk was unloaded (SOW 4.1). */
public final class GatherChunkLoadListener implements Listener {

    private final BlockRegenService regenService;

    public GatherChunkLoadListener(BlockRegenService regenService) {
        this.regenService = regenService;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        regenService.onChunkLoaded(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }
}
