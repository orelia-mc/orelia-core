package rpg.gathering.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.gathering.service.BlockRegenService;

import java.util.List;

/**
 * {@code /oladmin gathering resetregen confirm} - clears {@link BlockRegenService}'s pending
 * restore queue, in memory and in the database. Use it to drop stale tasks, e.g. ones queued
 * before an exclusion region was defined over that area. Nothing a player built is affected -
 * which blocks are exempt from regen is decided by
 * {@code gathering.yml: regen-exclusion.regions} and needs only {@code /oladmin reload}, not
 * this command. Requires the literal {@code confirm} argument since it clears every world at
 * once.
 */
public final class GatheringAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("resetregen");

    private final BlockRegenService regenService;
    private final MessageManager messages;

    public GatheringAdminCommand(BlockRegenService regenService, MessageManager messages) {
        this.regenService = regenService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            messages.send(sender, "gathering.usage", "label", label);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "resetregen" -> {
                if (!confirmed(args)) {
                    messages.send(sender, "gathering.resetregen-confirm", "label", label);
                    return true;
                }
                messages.send(sender, "gathering.resetregen-done", "regen", regenService.clearAll());
            }
            default -> messages.send(sender, "gathering.usage", "label", label);
        }
        return true;
    }

    private boolean confirmed(String[] args) {
        return args.length >= 2 && args[1].equalsIgnoreCase("confirm");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && SUBCOMMANDS.contains(args[0].toLowerCase())) {
            return TabCompletions.matching(List.of("confirm"), args[1]);
        }
        return List.of();
    }
}
