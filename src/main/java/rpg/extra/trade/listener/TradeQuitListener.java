package rpg.extra.trade.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.core.message.MessageManager;
import rpg.extra.trade.service.TradeService;

/** Cancels and refunds any in-progress trade the moment either side disconnects. */
public final class TradeQuitListener implements Listener {

    private final TradeService tradeService;
    private final MessageManager messages;

    public TradeQuitListener(TradeService tradeService, MessageManager messages) {
        this.tradeService = tradeService;
        this.messages = messages;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        tradeService.getSession(player.getUniqueId()).ifPresent(session -> {
            Player other = Bukkit.getPlayer(session.getOtherPlayer(player.getUniqueId()));
            if (other != null) {
                messages.send(other, "trade.cancelled-by-quit", "player", player.getName());
            }
        });
        tradeService.forceCancelIfTrading(player.getUniqueId());
    }
}
