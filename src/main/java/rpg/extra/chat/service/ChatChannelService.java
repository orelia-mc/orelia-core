package rpg.extra.chat.service;

import org.bukkit.entity.Player;
import rpg.extra.chat.ChatBroadcast;
import rpg.extra.chat.model.ChatChannel;
import rpg.extra.guild.service.GuildService;
import rpg.extra.party.service.PartyService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each online player's currently-selected default chat channel. In-memory only (not
 * persisted across restarts/relogs) - a player always starts back on {@link ChatChannel#PUBLIC},
 * same as any other session-scoped UX toggle.
 */
public final class ChatChannelService {

    public enum SwitchResult {
        OK, PARTY_REQUIRED, GUILD_REQUIRED, ADMIN_PERMISSION_REQUIRED
    }

    private final Map<UUID, ChatChannel> channels = new ConcurrentHashMap<>();
    private final PartyService partyService;
    private final GuildService guildService;

    public ChatChannelService(PartyService partyService, GuildService guildService) {
        this.partyService = partyService;
        this.guildService = guildService;
    }

    public ChatChannel getChannel(UUID playerId) {
        return channels.getOrDefault(playerId, ChatChannel.PUBLIC);
    }

    public SwitchResult switchChannel(Player player, ChatChannel channel) {
        switch (channel) {
            case PARTY -> {
                if (partyService.getParty(player.getUniqueId()).isEmpty()) {
                    return SwitchResult.PARTY_REQUIRED;
                }
            }
            case GUILD -> {
                if (guildService.getGuild(player.getUniqueId()).isEmpty()) {
                    return SwitchResult.GUILD_REQUIRED;
                }
            }
            case ADMIN -> {
                if (!player.hasPermission(ChatBroadcast.ADMIN_PERMISSION)) {
                    return SwitchResult.ADMIN_PERMISSION_REQUIRED;
                }
            }
            case PUBLIC -> {
            }
        }
        setChannel(player.getUniqueId(), channel);
        return SwitchResult.OK;
    }

    private void setChannel(UUID playerId, ChatChannel channel) {
        if (channel == ChatChannel.PUBLIC) {
            channels.remove(playerId);
        } else {
            channels.put(playerId, channel);
        }
    }

    /** Reverts to PUBLIC - used when a selected PARTY/GUILD channel's membership was lost since switching. */
    public void revertToPublic(UUID playerId) {
        channels.remove(playerId);
    }
}
