package rpg.core.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds every chat message through {@link ChatInputService#tryConsume} before anything else
 * sees it ({@link EventPriority#LOWEST}, so a captured line never reaches
 * {@code rpg.extra.chat.listener.ChatChannelListener} or orelia-serverutil's own chat
 * rendering) - cancels the event when the sender had a pending request, letting normal chat
 * processing continue untouched otherwise.
 */
public final class ChatInputListener implements Listener {

    private final ChatInputService service;

    public ChatInputListener(ChatInputService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (service.tryConsume(event.getPlayer().getUniqueId(), plain)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.cancel(event.getPlayer().getUniqueId());
    }
}
