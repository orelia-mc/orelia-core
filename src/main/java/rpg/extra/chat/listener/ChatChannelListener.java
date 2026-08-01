package rpg.extra.chat.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import rpg.core.message.MessageManager;
import rpg.extra.chat.ChatBroadcast;
import rpg.extra.chat.model.ChatChannel;
import rpg.extra.chat.service.ChatChannelService;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.service.GuildService;
import rpg.extra.party.model.Party;
import rpg.extra.party.service.PartyService;
import rpg.util.ColorUtil;

/**
 * Routes a player's typed chat message to their currently-selected channel (see
 * {@link ChatChannelService}) instead of the default public broadcast. Runs at
 * {@link EventPriority#LOW} - before orelia-serverutil's own {@code ChatModule} (unspecified/
 * NORMAL priority) - so a cancelled event here means serverutil's renderer never actually gets
 * broadcast to anyone. {@link ChatChannel#PUBLIC} is left completely untouched (event not
 * cancelled), so serverutil keeps owning public-chat formatting exactly as before this feature
 * existed.
 */
public final class ChatChannelListener implements Listener {

    private final ChatChannelService channelService;
    private final PartyService partyService;
    private final GuildService guildService;
    private final MessageManager messages;

    public ChatChannelListener(ChatChannelService channelService, PartyService partyService,
                                GuildService guildService, MessageManager messages) {
        this.channelService = channelService;
        this.partyService = partyService;
        this.guildService = guildService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChatChannel channel = channelService.getChannel(player.getUniqueId());
        if (channel == ChatChannel.PUBLIC) {
            return;
        }
        event.setCancelled(true);
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        switch (channel) {
            case PARTY -> {
                if (!sendToParty(player, plainMessage)) {
                    channelService.revertToPublic(player.getUniqueId());
                    messages.send(player, "chat.reverted-party");
                }
            }
            case GUILD -> {
                if (!sendToGuild(player, plainMessage)) {
                    channelService.revertToPublic(player.getUniqueId());
                    messages.send(player, "chat.reverted-guild");
                }
            }
            case ADMIN -> sendToAdmins(player, plainMessage);
            case PUBLIC -> {
            }
        }
    }

    private boolean sendToParty(Player sender, String message) {
        Party party = partyService.getParty(sender.getUniqueId()).orElse(null);
        if (party == null) {
            return false;
        }
        Component line = formatLine("chat.party-format", sender, message);
        ChatBroadcast.toParty(party, line);
        return true;
    }

    private boolean sendToGuild(Player sender, String message) {
        Guild guild = guildService.getGuild(sender.getUniqueId()).orElse(null);
        if (guild == null) {
            return false;
        }
        Component line = formatLine("chat.guild-format", sender, message);
        ChatBroadcast.toGuild(guild, line);
        return true;
    }

    private void sendToAdmins(Player sender, String message) {
        ChatBroadcast.toAdmins(formatLine("chat.admin-format", sender, message));
    }

    private Component formatLine(String key, Player sender, String message) {
        return ColorUtil.component(messages.format(key, "sender", sender.getName(), "message", message));
    }
}
