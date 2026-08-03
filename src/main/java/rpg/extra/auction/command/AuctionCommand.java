package rpg.extra.auction.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.auction.gui.AuctionGuiScreen;
import rpg.extra.auction.model.AuctionListing;
import rpg.extra.auction.service.AuctionService;
import rpg.gui.framework.GuiManager;

import java.util.List;

/**
 * {@code /ol auction [list|sell <price>|collect]} (SOW AuctionModule). Plain {@code /ol auction}
 * opens the browse/buy GUI.
 */
public final class AuctionCommand implements CommandExecutor {

    private static final long DEFAULT_DURATION_MILLIS = 1000L * 60 * 60 * 24 * 3;

    private final AuctionService auctionService;
    private final AuctionGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public AuctionCommand(AuctionService auctionService, AuctionGuiScreen guiScreen, GuiManager guiManager,
                           MessageManager messages) {
        this.auctionService = auctionService;
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
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            guiManager.open(player, guiScreen.build(player));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "sell" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.auction-sell");
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[1]);
                    AuctionService.ActionResult result = auctionService.list(player, price, DEFAULT_DURATION_MILLIS);
                    if (result == AuctionService.ActionResult.OK) {
                        messages.send(sender, "auction.listed");
                    } else {
                        messages.send(sender, "auction.list-failed", "reason", messages.format(result.reasonMessageKey()));
                    }
                } catch (NumberFormatException e) {
                    messages.send(sender, "auction.invalid-price");
                }
            }
            case "collect" -> {
                List<AuctionListing> collectable = auctionService.getCollectable(player.getUniqueId());
                if (collectable.isEmpty()) {
                    messages.send(sender, "auction.nothing-to-collect");
                    return true;
                }
                int collected = 0;
                for (AuctionListing listing : collectable) {
                    if (auctionService.collect(player, listing.getId()) == AuctionService.ActionResult.OK) {
                        collected++;
                    }
                }
                messages.send(sender, "auction.collected", "count", collected);
            }
            default -> messages.send(sender, "usage.auction");
        }
        return true;
    }
}
