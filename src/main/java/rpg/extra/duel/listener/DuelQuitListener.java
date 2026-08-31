package rpg.extra.duel.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.extra.duel.manager.DuelRequestManager;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;

/** A player quitting mid-duel forfeits it (the remaining player is declared the winner); a player with pending duel requests just has them cleared, same as friend/party/guild's own quit listeners. */
public final class DuelQuitListener implements Listener {

    private final DuelSessionManager sessionManager;
    private final DuelRequestManager requestManager;
    private final DuelDamageListener damageListener;

    public DuelQuitListener(DuelSessionManager sessionManager, DuelRequestManager requestManager, DuelDamageListener damageListener) {
        this.sessionManager = sessionManager;
        this.requestManager = requestManager;
        this.damageListener = damageListener;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        requestManager.clear(playerId);
        sessionManager.sessionOf(playerId).ifPresent(session -> {
            var opponentId = session.opponentOf(playerId);
            damageListener.resolveDuel(session, opponentId);
        });
    }
}
