package rpg.npc.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import rpg.npc.model.NpcData;

/**
 * Fired when a player interacts with a {@code GUILD_RECEPTIONIST} NPC. orelia-world has no
 * guild feature of its own (Guild lives in orelia-extra, which orelia-world cannot compile-
 * depend on - the dependency direction is orelia-extra -&gt; orelia-world -&gt; orelia-core) - this
 * is a plain hook event that's a harmless no-op if nothing is listening, i.e. orelia-extra
 * isn't installed. See {@code rpg.extra.guild.listener.NpcGuildInteractListener}.
 */
public final class NpcGuildInteractEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final NpcData npcData;

    public NpcGuildInteractEvent(Player player, NpcData npcData) {
        this.player = player;
        this.npcData = npcData;
    }

    public Player getPlayer() {
        return player;
    }

    public NpcData getNpcData() {
        return npcData;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
