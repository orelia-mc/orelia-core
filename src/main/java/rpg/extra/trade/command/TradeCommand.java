package rpg.extra.trade.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.trade.model.TradeSession;
import rpg.extra.trade.service.TradeService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ol trade <player>|accept|add|remove <index>|confirm|cancel|view} (SOW TradeModule).
 */
public final class TradeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("accept", "add", "remove", "confirm", "cancel", "view");

    private final TradeService tradeService;
    private final MessageManager messages;

    public TradeCommand(TradeService tradeService, MessageManager messages) {
        this.tradeService = tradeService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage.trade");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept" -> {
                TradeService.ActionResult result = tradeService.accept(player);
                if (result == TradeService.ActionResult.OK) {
                    messages.send(player, "trade.started");
                } else {
                    report(sender, result);
                }
            }
            case "add" -> report(sender, tradeService.addHeldItem(player));
            case "remove" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.trade-remove");
                    return true;
                }
                try {
                    report(sender, tradeService.removeOfferedItem(player, Integer.parseInt(args[1])));
                } catch (NumberFormatException e) {
                    messages.send(sender, "trade.invalid-number");
                }
            }
            case "confirm" -> {
                boolean executed = tradeService.confirm(player);
                messages.send(player, executed ? "trade.confirmed-executed" : "trade.confirmed-waiting");
            }
            case "cancel" -> report(sender, tradeService.cancel(player));
            case "view" -> showOffers(sender, player);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    messages.send(sender, "command.player-not-found", "player", args[0]);
                    return true;
                }
                TradeService.ActionResult result = tradeService.request(player, target);
                report(sender, result);
                if (result == TradeService.ActionResult.OK) {
                    messages.send(target, "trade.request-received", "player", player.getName());
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0];
            // First arg is either a subcommand or a target player name (default branch), so offer both.
            List<String> options = new ArrayList<>(TabCompletions.matching(SUBCOMMANDS, prefix));
            options.addAll(TabCompletions.onlinePlayerNames(prefix));
            return options;
        }
        return List.of();
    }

    private void showOffers(CommandSender sender, Player player) {
        TradeSession session = tradeService.getSession(player.getUniqueId()).orElse(null);
        if (session == null) {
            messages.send(sender, "trade.not-trading");
            return;
        }
        messages.send(sender, "trade.your-offer-header");
        printOffer(sender, session.offerOf(player.getUniqueId()).getItems());
        messages.send(sender, "trade.their-offer-header");
        printOffer(sender, session.offerOf(session.getOtherPlayer(player.getUniqueId())).getItems());
    }

    private void printOffer(CommandSender sender, java.util.List<ItemStack> items) {
        if (items.isEmpty()) {
            messages.send(sender, "trade.offer-empty");
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            messages.sendRaw(sender, "trade.offer-entry", "index", i, "type", item.getType(), "amount", item.getAmount());
        }
    }

    private void report(CommandSender sender, TradeService.ActionResult result) {
        if (result == TradeService.ActionResult.OK) {
            messages.send(sender, "command.ok");
            return;
        }
        String key = switch (result) {
            case ALREADY_TRADING -> "trade.already-trading";
            case NOT_TRADING -> "trade.not-trading";
            case NO_PENDING_REQUEST -> "trade.no-pending-request";
            case CANNOT_TARGET_SELF -> "trade.cannot-target-self";
            case EMPTY_HAND -> "trade.empty-hand";
            case INVALID_SLOT -> "trade.invalid-slot";
            case OK -> "command.ok";
        };
        messages.send(sender, key);
    }
}
