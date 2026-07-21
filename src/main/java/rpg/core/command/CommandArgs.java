package rpg.core.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared "if not specified, target myself" argument-resolution helper for admin-ish commands. */
public final class CommandArgs {

    private CommandArgs() {
    }

    /**
     * Returns {@code args[index]} if present, otherwise {@code sender}'s own name if it's a
     * player (or {@code null} for console with no argument given).
     */
    public static String resolvePlayerName(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            return args[index];
        }
        return sender instanceof Player player ? player.getName() : null;
    }
}
