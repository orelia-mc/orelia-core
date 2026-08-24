package rpg.extra.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.api.PlayerProfile;
import rpg.api.PlayerProfileApi;
import rpg.core.message.MessageManager;
import rpg.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a chat line's sender-name portion with a hover tooltip (level/job/guild/title, via
 * {@link PlayerProfileApi} - published by orelia-serverutil, soft dependency) and a
 * click-to-suggest {@code /ol msg <name> } - the "player name hover card" piece of
 * dynamic-chat-design.md's package 3. Used by every {@code {sender}}-templated chat line this
 * plugin sends (party/guild/admin - {@link rpg.extra.chat.listener.ChatChannelListener},
 * {@link rpg.extra.party.command.PartyCommand}, {@link rpg.extra.guild.command.GuildCommand},
 * {@link rpg.extra.chat.command.AdminChatCommand}), not just the {@link ChatChannelListener}
 * routing path - orelia-serverutil's own hover (public chat only, see its {@code ChatModule})
 * never reaches these since {@code ChatChannelListener} cancels the event before that renderer runs.
 *
 * <p>Falls back to a plain, click-only name Component if {@link PlayerProfileApi} isn't
 * published (orelia-serverutil not installed, or its core-integration bridge has nothing to
 * show) - never blocks sending the message over a missing soft dependency.
 */
public final class PlayerNameHover {

    private PlayerNameHover() {
    }

    /**
     * Splices {@code sender}'s decorated name into {@code key}'s {@code {sender}} slot (message
     * is substituted first, so it may itself contain literal {@code {sender}} text without being
     * mistaken for the template's own token). Non-player senders (e.g. console running
     * {@code /oladmin chat}) get a plain name Component - {@link PlayerProfileApi} has nothing
     * to look up for them anyway.
     */
    public static Component formatLine(MessageManager messages, String key, CommandSender sender, String message) {
        String template = messages.raw(key).replace("{message}", message);
        int senderStart = template.indexOf("{sender}");
        if (senderStart < 0) {
            return ColorUtil.component(template);
        }
        String before = template.substring(0, senderStart);
        String after = template.substring(senderStart + "{sender}".length());
        Component senderComponent = sender instanceof Player player ? decorate(player) : Component.text(sender.getName());
        return ColorUtil.component(before).append(senderComponent).append(ColorUtil.component(after));
    }

    private static Component decorate(Player sender) {
        String name = sender.getName();
        Component nameComponent = ColorUtil.componentWithSuggestCommand(name, "/ol msg " + name + " ");
        PlayerProfileApi api = Bukkit.getServicesManager().load(PlayerProfileApi.class);
        Optional<PlayerProfile> profile = api != null ? api.getProfile(sender.getUniqueId()) : Optional.empty();
        return profile.isPresent() ? nameComponent.hoverEvent(HoverEvent.showText(buildTooltip(profile.get()))) : nameComponent;
    }

    private static Component buildTooltip(PlayerProfile profile) {
        List<String> lines = new ArrayList<>();
        lines.add("&%7Lv.&%f" + profile.level() + " &%7" + profile.job());
        if (!profile.guildName().isEmpty()) {
            lines.add("&%7ギルド: &%f" + profile.guildName()
                    + (profile.guildTag().isEmpty() ? "" : " &%7[&%f" + profile.guildTag() + "&%7]"));
        }
        if (!profile.title().isEmpty()) {
            lines.add("&%7称号: &%f" + profile.title());
        }
        if (profile.inParty()) {
            lines.add("&%9パーティー中");
        }
        Component tooltip = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                tooltip = tooltip.append(Component.newline());
            }
            tooltip = tooltip.append(ColorUtil.component(lines.get(i)));
        }
        return tooltip;
    }
}
