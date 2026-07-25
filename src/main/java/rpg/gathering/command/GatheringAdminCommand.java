package rpg.gathering.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.gathering.service.BlockRegenService;
import rpg.gathering.service.PlacedBlockTrackingService;

import java.util.List;

/**
 * {@code /oladmin gathering resetregen confirm} - wipes the gathering module's regen-related
 * state ({@link BlockRegenService}'s pending restore queue and
 * {@link PlacedBlockTrackingService}'s hand-placed tracking), both in-memory and persisted.
 * Intended as a one-off cleanup for stale/inconsistent rows accumulated before
 * {@code BlockRegenService#cancelPending} existed (see the "手で置き直しても復活する" fix) -
 * every currently-tracked hand-placed block reverts to behaving as a natural gathering node
 * until placed again. Requires the literal {@code confirm} argument since it clears data for
 * every world at once.
 */
public final class GatheringAdminCommand implements CommandExecutor, TabCompleter {

    private final BlockRegenService regenService;
    private final PlacedBlockTrackingService trackingService;
    private final MessageManager messages;

    public GatheringAdminCommand(BlockRegenService regenService, PlacedBlockTrackingService trackingService,
                                  MessageManager messages) {
        this.regenService = regenService;
        this.trackingService = trackingService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("resetregen")) {
            messages.send(sender, "gathering.usage-resetregen", "label", label);
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            messages.send(sender, "gathering.resetregen-confirm", "label", label);
            return true;
        }
        int regenCount = regenService.clearAll();
        int placedCount = trackingService.clearAll();
        messages.send(sender, "gathering.resetregen-done", "regen", regenCount, "placed", placedCount);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(List.of("resetregen"), args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetregen")) {
            return TabCompletions.matching(List.of("confirm"), args[1]);
        }
        return List.of();
    }
}
