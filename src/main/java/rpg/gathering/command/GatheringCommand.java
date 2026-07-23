package rpg.gathering.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.gathering.config.LevelRadiusConfig;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.service.GatheringLevelService;
import rpg.job.manager.JobManager;
import rpg.job.model.Job;

/** {@code /ol gathering} - shows the sender's mining/woodcutting/farming levels and bulk radii. */
public final class GatheringCommand implements CommandExecutor {

    private final GatheringLevelService levelService;
    private final LevelRadiusConfig radiusConfig;
    private final JobManager jobManager;

    public GatheringCommand(GatheringLevelService levelService, LevelRadiusConfig radiusConfig, JobManager jobManager) {
        this.levelService = levelService;
        this.radiusConfig = radiusConfig;
        this.jobManager = jobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        for (GatherActionType activity : GatherActionType.values()) {
            int level = levelService.getLevel(player.getUniqueId(), activity);
            int radius = radiusConfig.radiusForLevel(level);
            String jobName = jobManager.getDefinition(activity.jobType()).map(Job::getDisplayName).orElse(activity.jobType().name());
            sender.sendMessage(Component.text(jobName + "レベル: ", NamedTextColor.GREEN)
                    .append(Component.text(String.valueOf(level), NamedTextColor.WHITE))
                    .append(Component.text(" / 一括範囲(半径): ", NamedTextColor.GREEN))
                    .append(Component.text(String.valueOf(radius), NamedTextColor.WHITE)));
        }
        return true;
    }
}
