package rpg.core.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An ordered, per-target queue of pending "somebody sent you something" entries (a friend
 * request's requester id, a party/guild invite's party/guild id, ...) - shared by
 * {@code rpg.extra.friend.manager.FriendRequestManager}, {@code rpg.extra.party.manager.PartyManager},
 * and {@code rpg.extra.guild.manager.GuildManager}, which previously each independently kept a
 * single-slot {@code Map<UUID, UUID>} (target -&gt; source): a second incoming request/invite
 * silently overwrote the first with no notice to the original sender, which is the actual cause
 * behind "my request never got accepted" reports as much as it is behind "the GUI can only show
 * one request at a time" - both trace to the same single-slot design. {@link LinkedHashSet}
 * keeps insertion order (oldest first) and de-duplicates a resend from the same source (an
 * existing entry's position doesn't move).
 */
public final class PendingQueue<T> {

    private final Map<UUID, LinkedHashSet<T>> byTarget = new ConcurrentHashMap<>();

    public void add(UUID target, T sourceId) {
        byTarget.computeIfAbsent(target, key -> new LinkedHashSet<>()).add(sourceId);
    }

    /** Every pending source for {@code target}, oldest first - for a GUI to list them all without consuming any. */
    public List<T> peekAll(UUID target) {
        LinkedHashSet<T> pending = byTarget.get(target);
        return pending == null ? List.of() : List.copyOf(pending);
    }

    /** The oldest (first-received) pending source, if any, without consuming it. */
    public Optional<T> peekOldest(UUID target) {
        LinkedHashSet<T> pending = byTarget.get(target);
        return pending == null || pending.isEmpty() ? Optional.empty() : Optional.of(pending.iterator().next());
    }

    /** Consumes (removes) the oldest pending source - what a no-argument "accept"/"decline" naturally means: the one that's been waiting longest. */
    public Optional<T> consumeOldest(UUID target) {
        return peekOldest(target).flatMap(sourceId -> consume(target, sourceId));
    }

    /** Consumes one specific pending source (not necessarily the oldest) - for a GUI/command letting the player pick which one to respond to. */
    public Optional<T> consume(UUID target, T sourceId) {
        LinkedHashSet<T> pending = byTarget.get(target);
        if (pending == null || !pending.remove(sourceId)) {
            return Optional.empty();
        }
        if (pending.isEmpty()) {
            byTarget.remove(target, pending);
        }
        return Optional.of(sourceId);
    }

    /** Drops every pending entry for {@code target} without consuming any - used on quit. */
    public void clear(UUID target) {
        byTarget.remove(target);
    }
}
