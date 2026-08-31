package rpg.extra.duel.service;

import org.bukkit.entity.Player;
import rpg.extra.duel.manager.DuelRequestManager;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Orchestrates the request -> accept/decline/cancel -> session-start flow. Mid-duel forfeit
 * (teleport/heal/reward/stats) is NOT owned here - that lives in DuelDamageListener.resolveDuel
 * (Task 8), invoked directly by DuelQuitListener (Task 9) and DuelCommand (Task 10); this class
 * only clears pending requests a departing player was involved in ({@link #clearPendingRequestsFor}). */
public final class DuelService {

    private final DuelRequestManager requestManager;
    private final DuelSessionManager sessionManager;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastRequestAtMillis = new ConcurrentHashMap<>();

    public DuelService(DuelRequestManager requestManager, DuelSessionManager sessionManager, long cooldownSeconds) {
        this.requestManager = requestManager;
        this.sessionManager = sessionManager;
        this.cooldownMillis = cooldownSeconds * 1000L;
    }

    public enum RequestResult { OK, ALREADY_PENDING, ON_COOLDOWN, SELF, ALREADY_IN_DUEL }

    public RequestResult request(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return RequestResult.SELF;
        }
        if (sessionManager.sessionOf(requester.getUniqueId()).isPresent()
                || sessionManager.sessionOf(target.getUniqueId()).isPresent()) {
            return RequestResult.ALREADY_IN_DUEL;
        }
        long last = lastRequestAtMillis.getOrDefault(requester.getUniqueId(), 0L);
        if (System.currentTimeMillis() - last < cooldownMillis) {
            return RequestResult.ON_COOLDOWN;
        }
        if (requestManager.hasPendingFrom(target.getUniqueId(), requester.getUniqueId())) {
            return RequestResult.ALREADY_PENDING;
        }
        requestManager.request(requester.getUniqueId(), target.getUniqueId());
        lastRequestAtMillis.put(requester.getUniqueId(), System.currentTimeMillis());
        return RequestResult.OK;
    }

    public enum AcceptResult { OK, NO_ARENA_FREE, NO_PENDING_REQUEST, ALREADY_IN_DUEL }

    /** {@code requesterId} - null accepts the oldest pending request (no-argument "/duel accept"), non-null accepts that specific one.
     * Resolves and validates the pending request (existence, then both players' active-session state) before consuming it, so a
     * request rejected for {@link AcceptResult#ALREADY_IN_DUEL} is left queued rather than silently dropped. */
    public AcceptResult accept(Player target, UUID requesterId, java.util.function.Function<UUID, Optional<Player>> onlinePlayerLookup) {
        UUID resolvedRequesterId;
        if (requesterId != null) {
            if (!requestManager.hasPendingFrom(target.getUniqueId(), requesterId)) {
                return AcceptResult.NO_PENDING_REQUEST;
            }
            resolvedRequesterId = requesterId;
        } else {
            Optional<UUID> oldest = requestManager.peekOldest(target.getUniqueId());
            if (oldest.isEmpty()) {
                return AcceptResult.NO_PENDING_REQUEST;
            }
            resolvedRequesterId = oldest.get();
        }

        if (sessionManager.sessionOf(target.getUniqueId()).isPresent()
                || sessionManager.sessionOf(resolvedRequesterId).isPresent()) {
            return AcceptResult.ALREADY_IN_DUEL;
        }

        Optional<UUID> consumed = requestManager.consume(target.getUniqueId(), resolvedRequesterId);
        if (consumed.isEmpty()) {
            // Raced with a decline/cancel/expiry between the checks above and here.
            return AcceptResult.NO_PENDING_REQUEST;
        }
        Optional<Player> requester = onlinePlayerLookup.apply(consumed.get());
        if (requester.isEmpty()) {
            return AcceptResult.NO_PENDING_REQUEST;
        }
        Optional<DuelSession> session = sessionManager.start(requester.get(), target);
        return session.isPresent() ? AcceptResult.OK : AcceptResult.NO_ARENA_FREE;
    }

    public boolean decline(Player target, UUID requesterId) {
        Optional<UUID> consumed = requesterId == null
                ? requestManager.consume(target.getUniqueId())
                : requestManager.consume(target.getUniqueId(), requesterId);
        return consumed.isPresent();
    }

    public boolean cancel(Player requester, UUID targetId) {
        return requestManager.consume(targetId, requester.getUniqueId()).isPresent();
    }

    /** Clears any pending requests a departing/departed player was involved in - callers still handle the active-session forfeit path separately (DuelDamageListener/DuelQuitListener own that, since it needs teleport/heal/reward logic this class doesn't have). */
    public void clearPendingRequestsFor(UUID playerId) {
        requestManager.clear(playerId);
    }
}
