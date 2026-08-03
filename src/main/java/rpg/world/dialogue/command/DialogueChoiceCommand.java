package rpg.world.dialogue.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.world.dialogue.service.DialogueSessionService;

/**
 * Internal command the clickable dialogue-choice chat lines run - not meant to be typed
 * by hand (not listed in help; SOW DialogueModule "選択肢").
 */
public final class DialogueChoiceCommand implements CommandExecutor {

    private final DialogueSessionService sessionService;

    public DialogueChoiceCommand(DialogueSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1) {
            return true;
        }
        try {
            sessionService.choose(player, Integer.parseInt(args[0]));
        } catch (NumberFormatException ignored) {
        }
        return true;
    }
}
