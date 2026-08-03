package rpg.extra.guild.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import rpg.core.message.MessageManager;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.service.GuildService;
import rpg.npc.event.NpcGuildInteractEvent;

/**
 * Bridges orelia-world's {@code GUILD_RECEPTIONIST} NPC hook into this module. orelia-world
 * can't compile-depend on orelia-extra (dependency direction is orelia-extra -&gt;
 * orelia-world -&gt; orelia-core), so it just fires {@link NpcGuildInteractEvent} on interact;
 * this listener is what actually reacts to it. Shows a condensed one-line summary rather than
 * the full member roster {@code /ol guild info} prints - a wall of text on every NPC click
 * would be worse than useful.
 */
public final class NpcGuildInteractListener implements Listener {

    private final GuildService guildService;
    private final MessageManager messages;

    public NpcGuildInteractListener(GuildService guildService, MessageManager messages) {
        this.guildService = guildService;
        this.messages = messages;
    }

    @EventHandler
    public void onGuildNpcInteract(NpcGuildInteractEvent event) {
        Player player = event.getPlayer();
        Guild guild = guildService.getGuild(player.getUniqueId()).orElse(null);
        if (guild == null) {
            messages.send(player, "guild.npc-no-guild");
            return;
        }
        messages.send(player, "guild.npc-summary", "name", truncate(guild.getName(), 24), "tag", truncate(guild.getTag(), 8),
                "role", guild.roleOf(player.getUniqueId()).getDisplayName(), "members", guild.getMembers().size());
    }

    /** Guild name/tag have no length limit at creation time - cap them here so this one-line summary can't run on forever. */
    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "…" : text;
    }
}
