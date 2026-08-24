package rpg.extra.party.listener;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.core.message.MessageManager;
import rpg.extra.party.manager.PartyManager;
import rpg.extra.party.service.PartyService;

import java.util.UUID;

/**
 * Drops a disconnecting player's pending invite, and leaves their party on their behalf
 * (auto-transferring leadership or disbanding as {@link PartyService#leaveOnQuit} decides),
 * announcing it to whoever remains.
 */
public final class PartyQuitListener implements Listener {

    private final PartyManager manager;
    private final PartyService partyService;
    private final MessageManager messages;

    public PartyQuitListener(PartyManager manager, PartyService partyService, MessageManager messages) {
        this.manager = manager;
        this.partyService = partyService;
        this.messages = messages;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        manager.clearInvite(playerId);

        partyService.leaveOnQuit(playerId).ifPresent(outcome -> {
            for (UUID memberId : outcome.party().getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null) {
                    continue;
                }
                messages.send(member, "party.member-left-quit", "player", player.getName());
                if (outcome.leadershipTransferred()) {
                    OfflinePlayer newLeader = Bukkit.getOfflinePlayer(outcome.party().getLeaderId());
                    messages.send(member, "party.leadership-transferred-quit", "player", newLeader.getName());
                }
            }
        });
    }
}
