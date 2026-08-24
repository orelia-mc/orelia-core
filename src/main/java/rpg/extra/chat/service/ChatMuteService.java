package rpg.extra.chat.service;

import rpg.extra.chat.model.ChatBadge;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which {@link ChatBadge} categories a player has personally muted. In-memory only, same
 * philosophy as {@link ChatChannelService} - a player who never mutes anything never gets an
 * entry, and the whole map is dropped on server restart. Unlike {@code ChatChannelService} this
 * intentionally does *not* clear a player's entry on quit, so a mute set survives a relog within
 * the same server run (only a full restart resets it) - see dynamic-chat-design.md for the
 * reasoning on why this stays memory-only for now.
 */
public final class ChatMuteService {

    private final Map<UUID, EnumSet<ChatBadge>> muted = new ConcurrentHashMap<>();

    public boolean isMuted(UUID playerId, ChatBadge category) {
        EnumSet<ChatBadge> categories = muted.get(playerId);
        return categories != null && categories.contains(category);
    }

    /** Flips {@code category}'s mute state for {@code playerId} and returns the new state (true = now muted). */
    public boolean toggle(UUID playerId, ChatBadge category) {
        EnumSet<ChatBadge> categories = muted.computeIfAbsent(playerId, id -> EnumSet.noneOf(ChatBadge.class));
        boolean nowMuted;
        if (categories.remove(category)) {
            nowMuted = false;
        } else {
            categories.add(category);
            nowMuted = true;
        }
        if (categories.isEmpty()) {
            muted.remove(playerId);
        }
        return nowMuted;
    }

    public Set<ChatBadge> getMuted(UUID playerId) {
        return muted.getOrDefault(playerId, EnumSet.noneOf(ChatBadge.class));
    }
}
