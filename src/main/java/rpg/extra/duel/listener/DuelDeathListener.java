package rpg.extra.duel.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;

/**
 * A duelist who dies for real - a third party's hit, or environmental damage (fall/lava/void/
 * fire in the arena), none of which {@link DuelDamageListener} ever sees since it only handles
 * {@code EntityDamageByEntityEvent} between the two duelists - would otherwise leave the
 * {@link DuelSession} orphaned: still tracked in {@code DuelSessionManager#sessionsByPlayer} for
 * both players and its arena still marked occupied. Combined with the {@code ALREADY_IN_DUEL}
 * guard in {@link rpg.extra.duel.service.DuelService}, that's a permanent lockout of both players
 * (and the arena) until someone manually runs {@code /ol duel forfeit}. This does NOT cancel the
 * death - by the time {@link PlayerDeathEvent} fires, real HP already hit 0, so the point here is
 * cleanup after the fact (ending the session, freeing the arena), not prevention.
 */
public final class DuelDeathListener implements Listener {

    private final DuelSessionManager sessionManager;
    private final DuelDamageListener damageListener;

    public DuelDeathListener(DuelSessionManager sessionManager, DuelDamageListener damageListener) {
        this.sessionManager = sessionManager;
        this.damageListener = damageListener;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        var playerId = event.getEntity().getUniqueId();
        sessionManager.sessionOf(playerId).ifPresent(session -> {
            var opponentId = session.opponentOf(playerId);
            damageListener.resolveDuel(session, opponentId);
        });
    }
}
