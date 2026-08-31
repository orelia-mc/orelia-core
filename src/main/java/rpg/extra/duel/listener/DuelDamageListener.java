package rpg.extra.duel.listener;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import rpg.api.StatusApi;
import rpg.core.message.MessageManager;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;
import rpg.extra.duel.service.DuelStatsService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs at {@link EventPriority#HIGH}, after {@code rpg.monster.listener.CombatDamageListener}'s
 * own {@code EventPriority.LOW} handler has already resolved final damage and reduced the
 * victim's scaled {@code currentHp} (see that class's {@code resolveFinalDamage} - this happens
 * synchronously, so by the time this handler runs the scaled HP drop has already landed). If the
 * victim is now at lethal scaled HP <b>and</b> both participants are in the same active duel,
 * cancels the event (preventing vanilla death/knockback) and resolves the duel instead of
 * letting it kill the loser for real.
 */
public final class DuelDamageListener implements Listener {

    private final DuelSessionManager sessionManager;
    private final DuelStatsService statsService;
    private final StatusApi statusApi;
    private final Economy economy;
    private final MessageManager messages;
    private final double rewardMoney;

    public DuelDamageListener(DuelSessionManager sessionManager, DuelStatsService statsService, StatusApi statusApi,
                               Economy economy, MessageManager messages, double rewardMoney) {
        this.sessionManager = sessionManager;
        this.statsService = statsService;
        this.statusApi = statusApi;
        this.economy = economy;
        this.messages = messages;
        this.rewardMoney = rewardMoney;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        Optional<DuelSession> maybeSession = sessionManager.sessionOf(victim.getUniqueId());
        if (maybeSession.isEmpty()) {
            return;
        }
        DuelSession session = maybeSession.get();
        if (!session.involves(attacker.getUniqueId())) {
            return; // third party hit a duelist - not a duel-ending blow
        }
        double currentHp = statusApi.getCurrentHp(victim.getUniqueId()).orElse(0.0);
        if (currentHp > 0) {
            return;
        }
        event.setCancelled(true);
        resolveDuel(session, attacker.getUniqueId());
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /** Ends {@code session}, declaring {@code winnerId} the winner - teleports both back, heals both to full, pays the reward, records stats, announces the result, and frees the arena. Also called directly by {@code DuelQuitListener} for the forfeit-on-quit path. */
    public void resolveDuel(DuelSession session, UUID winnerId) {
        UUID loserId = session.opponentOf(winnerId);
        teleportBackAndHeal(session, session.getPlayerA());
        teleportBackAndHeal(session, session.getPlayerB());
        economy.depositPlayer(Bukkit.getOfflinePlayer(winnerId), rewardMoney);
        statsService.recordResult(winnerId, loserId);
        sessionManager.end(session);
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        if (winner != null) {
            // No dedicated win/loss Sound constant exists for this message yet (unlike
            // ShopGuiScreen's sendWithSound call, which passes a real Sound) - MessageManager's
            // sendWithSound(..., null, ...) forwards straight into Player#playSound(Location,
            // Sound, float, float), which throws on a null Sound rather than tolerating it, so a
            // plain send() (no sound cue) is used here instead of sendWithSound with a null third
            // argument, which would have thrown and skipped the loser's message below.
            messages.send(winner, "duel.won", "opponent", loser != null ? loser.getName() : loserId.toString(), "reward", rewardMoney);
        }
        if (loser != null) {
            messages.send(loser, "duel.lost", "opponent", winner != null ? winner.getName() : winnerId.toString());
        }
    }

    private void teleportBackAndHeal(DuelSession session, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.teleport(session.getReturnLocation(playerId));
        Map<String, Double> stats = statusApi.getFinalStats(playerId);
        double maxHp = stats.getOrDefault("HP", 0.0);
        double currentHp = statusApi.getCurrentHp(playerId).orElse(0.0);
        double missing = maxHp - currentHp;
        if (missing > 0) {
            statusApi.heal(playerId, missing);
        }
    }
}
