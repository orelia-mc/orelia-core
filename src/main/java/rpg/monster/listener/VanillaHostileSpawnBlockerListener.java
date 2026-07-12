package rpg.monster.listener;

import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Cancels vanilla natural/spawner-block hostile mob spawns (SOW follow-up: enemies should
 * only appear at admin-placed {@code rpg.monster.spawnpoint} locations, not randomly).
 * Passive animals and Orelia's own {@code MonsterSpawnService} spawns (reason CUSTOM) are
 * untouched.
 */
public final class VanillaHostileSpawnBlockerListener implements Listener {

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.setCancelled(true);
        }
    }
}
