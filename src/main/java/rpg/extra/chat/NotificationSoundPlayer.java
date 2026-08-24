package rpg.extra.chat;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Shared {@code Sound.valueOf} + play-for-one-player helper for single-target notifications
 * (mail unread, trade/party/guild invite received, ...) - the same fail-open pattern
 * {@code rpg.extra.mail.listener.MailUnreadJoinListener} and orelia-serverutil's
 * {@code MentionService} each grew independently, pulled out here now that a third+fourth call
 * site need it too rather than copying the try/catch a third time.
 *
 * <p>Callers on a Bukkit-event thread that isn't guaranteed to be the main thread (e.g. an
 * {@code AsyncChatEvent} listener) must still hop back via {@code Bukkit.getScheduler().runTask}
 * themselves before calling this - it does not do that for them, since most of today's callers
 * (command executors) already run on the main thread and forcing an extra scheduler hop there
 * would be pure overhead.
 */
public final class NotificationSoundPlayer {

    private NotificationSoundPlayer() {
    }

    /** No-ops (with a warning logged) if {@code rawSoundName} isn't a valid {@link Sound} constant. */
    public static void play(Player player, boolean enabled, String rawSoundName, double volume, double pitch, Logger logger) {
        if (!enabled || rawSoundName == null || rawSoundName.isBlank()) {
            return;
        }
        Sound sound;
        try {
            sound = Sound.valueOf(rawSoundName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("notify-sound.name (\"" + rawSoundName + "\") isn't a valid org.bukkit.Sound constant - skipping the notify sound.");
            return;
        }
        player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
    }
}
