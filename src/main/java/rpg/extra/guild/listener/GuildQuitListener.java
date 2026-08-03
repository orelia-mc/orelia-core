package rpg.extra.guild.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.extra.guild.manager.GuildManager;

public final class GuildQuitListener implements Listener {

    private final GuildManager manager;

    public GuildQuitListener(GuildManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearInvite(event.getPlayer().getUniqueId());
    }
}
