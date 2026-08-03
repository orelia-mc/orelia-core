package rpg.extra.pet.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.extra.pet.manager.PetManager;

/** Despawns a player's active pet the moment they disconnect. */
public final class PetQuitListener implements Listener {

    private final PetManager petManager;

    public PetQuitListener(PetManager petManager) {
        this.petManager = petManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        petManager.despawn(event.getPlayer().getUniqueId());
    }
}
