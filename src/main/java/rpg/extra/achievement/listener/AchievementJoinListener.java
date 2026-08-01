package rpg.extra.achievement.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import rpg.extra.achievement.service.AchievementService;

/** Checks a player's achievements as soon as they join, rather than waiting for the next periodic sweep. */
public final class AchievementJoinListener implements Listener {

    private final AchievementService achievementService;

    public AchievementJoinListener(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        achievementService.checkPlayer(event.getPlayer());
    }
}
