package rpg.extra.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import rpg.extra.chat.model.ChatBadge;
import rpg.extra.chat.service.ChatMuteService;
import rpg.extra.guild.model.Guild;
import rpg.extra.party.model.Party;

import java.util.UUID;

/**
 * Sends an already-formatted chat line to a party/guild/admin audience. Stateless and
 * dependency-free (just Bukkit + the plain data models) so both {@code ChatChannelListener}
 * (routes a player's typed message per their selected channel) and the one-off senders
 * ({@code /oladmin chat}, {@code /ol party chat}, {@code /ol guild chat}) can share it without
 * needing to look up a central chat service. {@code muteService} is likewise just a parameter,
 * not a field - callers pass whichever instance they already have (see
 * {@code OreliaPlugin#getChatMuteService}) rather than this class holding one itself.
 */
public final class ChatBroadcast {

    public static final String ADMIN_PERMISSION = "orelia.admin";

    private ChatBroadcast() {
    }

    /** {@code badge} is prepended to {@code line} once and muted members are skipped entirely - see {@link ChatBadge}. */
    public static void toParty(Party party, Component line, ChatBadge badge, ChatMuteService muteService) {
        Component decorated = badge.decorate(line);
        for (UUID memberId : party.getMembers()) {
            if (muteService.isMuted(memberId, badge)) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(decorated);
            }
        }
    }

    /** {@code badge} is prepended to {@code line} once and muted members are skipped entirely - see {@link ChatBadge}. */
    public static void toGuild(Guild guild, Component line, ChatBadge badge, ChatMuteService muteService) {
        Component decorated = badge.decorate(line);
        for (UUID memberId : guild.getMembers().keySet()) {
            if (muteService.isMuted(memberId, badge)) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(decorated);
            }
        }
    }

    public static void toAdmins(Component line) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(ADMIN_PERMISSION))
                .forEach(player -> player.sendMessage(line));
    }
}
