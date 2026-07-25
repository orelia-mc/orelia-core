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
 * {@code /oladmin gathering <resetregen|resetplaced> confirm} - admin escape hatches for the
 * gathering module's two persisted coordinate tables. They are deliberately *separate*
 * subcommands because they do opposite things:
 *
 * <ul>
 *   <li>{@code resetregen} clears {@link BlockRegenService}'s pending restore queue - the
 *       blocks waiting to grow back. Use it to drop stale tasks (e.g. ones queued before
 *       {@code BlockRegenService#cancelPending} existed, which forced hand-placed blocks back
 *       into gathering nodes). Nothing a player built is affected.</li>
 *   <li>{@code resetplaced} clears {@link PlacedBlockTrackingService}'s hand-placed tracking -
 *       the *exclusion* list. Every log players placed by hand loses its regen exemption and
 *       goes back to behaving as a natural node, so this is the destructive one and is only
 *       ever what you want when the tracking table itself is wrong.</li>
 * </ul>
 *
 * Both require the literal {@code confirm} argument since they clear every world at once.
 */
public final class GatheringAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("resetregen", "resetplaced");

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
            case "resetplaced" -> {
                if (!confirmed(args)) {
                    messages.send(sender, "gathering.resetplaced-confirm", "label", label);
                    return true;
                }
                messages.send(sender, "gathering.resetplaced-done", "placed", trackingService.clearAll());
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
