package rpg.extra.auction.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.auction.model.AuctionListing;
import rpg.extra.auction.service.AuctionService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;
import rpg.util.MoneyFormat;

import java.util.List;

/**
 * Browse/buy screen for active auction listings (SOW AuctionModule). Reuses orelia-core's
 * generic {@code Gui}/{@code GuiButton} framework directly, the same way MailGuiScreen does.
 */
public final class AuctionGuiScreen {

    private final AuctionService auctionService;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public AuctionGuiScreen(AuctionService auctionService, GuiManager guiManager, MessageManager messages) {
        this.auctionService = auctionService;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    public Gui build(Player viewer) {
        Gui gui = new Gui(ColorUtil.colorize("&%8オークション"), 54);
        List<AuctionListing> listings = auctionService.getActiveListings();

        int slot = 0;
        for (AuctionListing listing : listings) {
            if (slot >= 54) {
                break;
            }
            boolean own = listing.getSellerId().equals(viewer.getUniqueId());
            gui.set(slot++, new GuiButton(new ItemBuilder(listing.getItem().getType())
                    .name((own ? "&%e" : "&%f") + listing.getDisplayName())
                    .lore(List.of(
                            "&%7出品者: &%f" + listing.getSellerName(),
                            "&%7価格: &%6" + MoneyFormat.format(listing.getPrice()),
                            own ? "&%cクリックでキャンセル" : "&%aクリックで購入"))
                    .build(), (clicker, clickType) -> {
                if (own) {
                    AuctionService.ActionResult result = auctionService.cancel(clicker, listing.getId());
                    if (result == AuctionService.ActionResult.OK) {
                        messages.send(clicker, "auction.cancelled");
                    } else {
                        messages.send(clicker, "auction.cancel-failed", "reason", messages.format(result.reasonMessageKey()));
                    }
                } else {
                    AuctionService.ActionResult result = auctionService.buy(clicker, listing.getId());
                    if (result == AuctionService.ActionResult.OK) {
                        messages.send(clicker, "auction.bought", "item", listing.getDisplayName(), "price", MoneyFormat.format(listing.getPrice()));
                    } else {
                        messages.send(clicker, "auction.buy-failed", "reason", messages.format(result.reasonMessageKey()));
                    }
                }
                // The listing this button represents may have just sold/been cancelled -
                // the whole screen needs re-laying-out (remaining listings shift slots),
                // not just this one icon, so reopen rather than patch a single slot.
                guiManager.open(clicker, build(clicker));
            }));
        }
        if (listings.isEmpty()) {
            gui.set(22, new GuiButton(new ItemBuilder(Material.BARRIER).name(messages.format("auction.no-listings")).build(), (clicker, clickType) -> {
            }));
        }
        return gui;
    }
}
