package rpg.extra.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import rpg.extra.guild.model.Guild;
import rpg.extra.party.model.Party;

import java.util.UUID;

/**
 * Sends an already-formatted chat line to a party/guild/admin audience. Stateless and
 * dependency-free (just Bukkit + the plain data models) so both {@code ChatChannelListener}
 * (routes a player's typed message per their selected channel) and the one-off senders
 * ({@code /oladmin chat}, {@code /ol party chat}, {@code /ol guild chat}) can share it without
 * needing to look up a central chat service.
 */
public final class ChatBroadcast {

    public static final String ADMIN_PERMISSION = "orelia.admin";

    private ChatBroadcast() {
    }

    public static void toParty(Party party, Component line) {
        for (UUID memberId : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(line);
            }
        }
    }

    public static void toGuild(Guild guild, Component line) {
        for (UUID memberId : guild.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(line);
            }
        }
    }

    public static void toAdmins(Component line) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(ADMIN_PERMISSION))
                .forEach(player -> player.sendMessage(line));
    }
}
