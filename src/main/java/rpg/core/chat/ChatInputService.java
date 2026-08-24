package rpg.core.chat;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import rpg.core.message.MessageManager;
import rpg.core.scheduler.SchedulerService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures a player's next chat line as free-text input for a GUI flow (guild/party name, a
 * player name to invite, ...) instead of routing it through the normal chat pipeline - used so
 * a GUI button can ask "type the guild name in chat" and get the answer back, rather than the
 * suggest-command pattern (pre-fill a command in the player's chat box) this codebase otherwise
 * uses everywhere text can't fit in an inventory click. One pending request per player; a new
 * request silently replaces (cancels) whatever that player was already being asked for.
 *
 * <p>{@link ChatInputListener} is the other half - it's the one actually watching
 * {@code AsyncChatEvent} and calling {@link #tryConsume}.
 */
public final class ChatInputService {

    private static final long DEFAULT_TIMEOUT_TICKS = 30L * 20L;

    private record Pending(Consumer<String> onInput, BukkitTask timeoutTask) {
    }

    private final SchedulerService schedulerService;
    private final MessageManager messages;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInputService(SchedulerService schedulerService, MessageManager messages) {
        this.schedulerService = schedulerService;
        this.messages = messages;
    }

    /**
     * Registers {@code onInput} to receive {@code player}'s next chat line (never broadcast as
     * a normal chat message - see {@link ChatInputListener}). Auto-expires after 30 seconds
     * with a "timed out" notice if the player never types anything.
     */
    public void request(Player player, Consumer<String> onInput) {
        UUID playerId = player.getUniqueId();
        cancel(playerId);
        BukkitTask timeoutTask = schedulerService.runLater(() -> {
            if (pending.remove(playerId) != null) {
                messages.send(player, "chat-input.timed-out");
            }
        }, DEFAULT_TIMEOUT_TICKS);
        pending.put(playerId, new Pending(onInput, timeoutTask));
    }

    /** Called by {@link ChatInputListener}; returns true (and consumes the pending request) if {@code playerId} had one waiting. */
    boolean tryConsume(UUID playerId, String message) {
        Pending request = pending.remove(playerId);
        if (request == null) {
            return false;
        }
        request.timeoutTask().cancel();
        request.onInput().accept(message);
        return true;
    }

    /** Drops any pending request for {@code playerId} without invoking its callback - used on quit and when a new request replaces an old one. */
    public void cancel(UUID playerId) {
        Pending request = pending.remove(playerId);
        if (request != null) {
            request.timeoutTask().cancel();
        }
    }
}
