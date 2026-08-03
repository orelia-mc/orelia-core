package rpg.extra.ranking.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.ranking.gui.RankingGuiScreen;
import rpg.gui.framework.GuiManager;

/**
 * {@code /ol ranking} opens the level leaderboard GUI (SOW RankingModule).
 */
public final class RankingCommand implements CommandExecutor {

    private final RankingGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public RankingCommand(RankingGuiScreen guiScreen, GuiManager guiManager, MessageManager messages) {
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        guiManager.open(player, guiScreen.build());
        return true;
    }
}
