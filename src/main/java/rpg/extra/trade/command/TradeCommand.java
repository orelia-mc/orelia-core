package rpg.extra.trade.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.chat.NotificationSoundPlayer;
import rpg.extra.trade.config.TradeConfig;
import rpg.extra.trade.model.TradeSession;
import rpg.extra.trade.service.TradeService;
import rpg.extra.util.ItemDisplayNames;
import rpg.util.ColorUtil;
import rpg.util.MoneyFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * {@code /ol trade <player>|accept|add|remove <index>|money <amount>|confirm|cancel|view} (SOW TradeModule).
 */
public final class TradeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("accept", "add", "remove", "money", "confirm", "cancel", "view");

    private final TradeService tradeService;
    private final MessageManager messages;
    private final TradeConfig tradeConfig;
    private final Logger logger;

    public TradeCommand(TradeService tradeService, MessageManager messages, TradeConfig tradeConfig, Logger logger) {
        this.tradeService = tradeService;
        this.messages = messages;
        this.tradeConfig = tradeConfig;
        this.logger = logger;
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
            case "add" -> {
                TradeService.ActionResult result = tradeService.addHeldItem(player);
                if (result == TradeService.ActionResult.OK) {
                    messages.send(player, "trade.item-added");
                } else {
                    report(sender, result);
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.trade-remove");
                    return true;
                }
                try {
                    TradeService.ActionResult result = tradeService.removeOfferedItem(player, Integer.parseInt(args[1]));
                    if (result == TradeService.ActionResult.OK) {
                        messages.send(player, "trade.item-removed");
                    } else {
                        report(sender, result);
                    }
                } catch (NumberFormatException e) {
                    messages.send(sender, "trade.invalid-number");
                }
            }
            case "money" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.trade-money");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    TradeService.ActionResult result = tradeService.setOfferedMoney(player, amount);
                    if (result == TradeService.ActionResult.OK) {
                        messages.send(player, "trade.money-set", "money", MoneyFormat.format(amount));
                    } else {
                        report(sender, result);
                    }
                } catch (NumberFormatException e) {
                    messages.send(sender, "trade.invalid-number");
                }
            }
            case "confirm" -> {
                TradeService.ActionResult result = tradeService.confirm(player);
                if (result == TradeService.ActionResult.OK) {
                    messages.send(player, "trade.confirmed-executed");
                } else if (result == TradeService.ActionResult.WAITING_FOR_OTHER) {
                    messages.send(player, "trade.confirmed-waiting");
                } else {
                    report(sender, result);
                }
            }
            case "cancel" -> {
                TradeService.ActionResult result = tradeService.cancel(player);
                if (result == TradeService.ActionResult.OK) {
                    messages.send(player, "trade.cancelled");
                } else {
                    report(sender, result);
                }
            }
            case "view" -> showOffers(sender, player);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    messages.send(sender, "command.player-not-found", "player", args[0]);
                    return true;
                }
                TradeService.ActionResult result = tradeService.request(player, target);
                if (result == TradeService.ActionResult.OK) {
                    messages.send(player, "trade.request-sent", "player", target.getName());
                    messages.send(target, "trade.request-received", "player", player.getName());
                    // Always plays when config-enabled - a system-driven notification, not a
                    // user-sent chat line, so /chat mute never affects it (trade has no chat
                    // channel of its own to mute). See ChatBadge's own doc comment.
                    NotificationSoundPlayer.play(target, tradeConfig.isNotifySoundEnabled(),
                            tradeConfig.getNotifySoundName(), tradeConfig.getNotifySoundVolume(),
                            tradeConfig.getNotifySoundPitch(), logger);
                } else {
                    report(sender, result);
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
        printOffer(sender, session.offerOf(player.getUniqueId()));
        messages.send(sender, "trade.their-offer-header");
        printOffer(sender, session.offerOf(session.getOtherPlayer(player.getUniqueId())));
    }

    private void printOffer(CommandSender sender, rpg.extra.trade.model.TradeOffer offer) {
        List<ItemStack> items = offer.getItems();
        if (items.isEmpty()) {
            messages.send(sender, "trade.offer-empty");
        }
        for (int i = 0; i < items.size(); i++) {
            sender.sendMessage(offerEntryComponent(i, items.get(i)));
        }
        if (offer.getMoney() > 0) {
            messages.send(sender, "trade.offer-money", "money", MoneyFormat.format(offer.getMoney()));
        }
    }

    /**
     * Builds {@code trade.offer-entry} as a Component instead of going through
     * {@link MessageManager#sendRaw} - the item-name portion needs a {@code HoverEvent.showItem}
     * (vanilla's own {@code [Item]} tooltip) attached, which the string-only formatter can't do.
     */
    private Component offerEntryComponent(int index, ItemStack item) {
        String template = messages.raw("trade.offer-entry")
                .replace("{index}", String.valueOf(index))
                .replace("{amount}", String.valueOf(item.getAmount()));
        int typeStart = template.indexOf("{type}");
        if (typeStart < 0) {
            return ColorUtil.component(template);
        }
        String before = template.substring(0, typeStart);
        String after = template.substring(typeStart + "{type}".length());
        return ColorUtil.component(before)
                .append(ColorUtil.componentWithItemHover(ItemDisplayNames.of(item), item))
                .append(ColorUtil.component(after));
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
            case WAITING_FOR_OTHER -> "trade.confirmed-waiting";
            case INSUFFICIENT_FUNDS -> "trade.insufficient-funds";
            case MONEY_UNSUPPORTED -> "trade.money-unsupported";
            case INVALID_AMOUNT -> "trade.invalid-amount";
            case TOO_MANY_ITEMS -> "trade.too-many-items";
            case OK -> "command.ok";
        };
        messages.send(sender, key);
    }
}
