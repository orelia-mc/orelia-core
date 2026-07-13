package rpg.gathering.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.gathering.config.LevelRadiusConfig;
import rpg.gathering.service.GatheringLevelService;

/** {@code /ol gathering} - shows the sender's gathering/farming level and current bulk radius. */
public final class GatheringCommand implements CommandExecutor {

    private final GatheringLevelService levelService;
    private final LevelRadiusConfig radiusConfig;

    public GatheringCommand(GatheringLevelService levelService, LevelRadiusConfig radiusConfig) {
        this.levelService = levelService;
        this.radiusConfig = radiusConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        int level = levelService.getLevel(player.getUniqueId());
        int radius = radiusConfig.radiusForLevel(level);
        sender.sendMessage(ChatColor.GREEN + "採取/農業レベル: " + ChatColor.WHITE + level
                + ChatColor.GREEN + " / 一括範囲(半径): " + ChatColor.WHITE + radius);
        return true;
    }
}
