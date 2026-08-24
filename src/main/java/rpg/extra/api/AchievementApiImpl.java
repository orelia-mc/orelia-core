package rpg.extra.api;

import org.bukkit.entity.Player;
import rpg.extra.achievement.AchievementModule;

final class AchievementApiImpl implements AchievementApi {

    private final AchievementModule module;

    AchievementApiImpl(AchievementModule module) {
        this.module = module;
    }

    @Override
    public void openGui(Player player) {
        module.openGui(player);
    }
}
