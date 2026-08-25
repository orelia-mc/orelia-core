package rpg.extra.auction.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.auction.model.AuctionListing;
import rpg.extra.auction.service.AuctionService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.gui.framework.GuiPageLayout;
import rpg.gui.framework.GuiPaginator;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;
import rpg.util.MoneyFormat;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Browse/buy screen for active auction listings (SOW AuctionModule). Reuses orelia-core's
 * generic {@code Gui}/{@code GuiButton} framework directly, the same way MailGuiScreen does.
 * Paginated via {@code GuiPaginator} (bottom row reserved for prev/next) now that the auction
 * house can hold more than 54 active listings at once.
 */
public final class AuctionGuiScreen {

    private static final GuiPageLayout LAYOUT = new GuiPageLayout(IntStream.range(0, 45).toArray(), 45, 53);

    private final AuctionService auctionService;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public AuctionGuiScreen(AuctionService auctionService, GuiManager guiManager, MessageManager messages) {
        this.auctionService = auctionService;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    public Gui build(Player viewer) {
        return build(viewer, 0);
    }

    private Gui build(Player viewer, int page) {
        Gui gui = new Gui(ColorUtil.colorize("&%8オークション"), 54);
        List<AuctionListing> listings = auctionService.getActiveListings();

        if (listings.isEmpty()) {
            gui.set(22, new GuiButton(new ItemBuilder(Material.BARRIER).name(messages.format("auction.no-listings")).build(), (clicker, clickType) -> {
            }));
            return gui;
        }

        GuiPaginator.placePage(guiManager, gui, LAYOUT, listings, page,
                listing -> listingButton(listing, viewer, page), p -> build(viewer, p));
        return gui;
    }

    private GuiButton listingButton(AuctionListing listing, Player viewer, int page) {
        boolean own = listing.getSellerId().equals(viewer.getUniqueId());
        boolean bidListing = listing.getType() == AuctionListing.ListingType.BID;
        return new GuiButton(new ItemBuilder(listing.getItem().getType())
                .name((own ? "&%e" : "&%f") + listing.getDisplayName())
                .lore(listingLore(listing, own, bidListing))
                .build(), (clicker, clickType) -> {
            if (own) {
                AuctionService.ActionResult result = auctionService.cancel(clicker, listing.getId());
                if (result == AuctionService.ActionResult.OK) {
                    messages.send(clicker, "auction.cancelled");
                } else {
                    messages.send(clicker, "auction.cancel-failed", "reason", messages.format(result.reasonMessageKey()));
                }
            } else if (bidListing) {
                // GUI click = quick-bid at the computed minimum increment (same formula the
                // /ol auction bid command validates against, via the one shared
                // AuctionService#minimumNextBid method) - a custom amount still needs the command.
                AuctionService.ActionResult result = auctionService.bid(clicker, listing.getId(), auctionService.minimumNextBid(listing));
                if (result == AuctionService.ActionResult.OK) {
                    messages.send(clicker, "auction.bid-placed", "price", auctionService.minimumNextBid(listing));
                } else {
                    messages.send(clicker, "auction.bid-failed", "reason", messages.format(result.reasonMessageKey()));
                }
            } else {
                AuctionService.ActionResult result = auctionService.buy(clicker, listing.getId());
                if (result == AuctionService.ActionResult.OK) {
                    sendBoughtMessage(clicker, listing);
                } else {
                    messages.send(clicker, "auction.buy-failed", "reason", messages.format(result.reasonMessageKey()));
                }
            }
            // The listing this button represents may have just sold/been bid on/been
            // cancelled - the whole screen needs re-laying-out (remaining listings shift
            // slots), not just this one icon, so reopen rather than patch a single slot.
            guiManager.open(clicker, build(clicker, page));
        });
    }

    private List<String> listingLore(AuctionListing listing, boolean own, boolean bidListing) {
        String priceLine = bidListing
                ? (listing.getCurrentBidAmount() != null
                        ? "&%7現在の入札額: &%6" + MoneyFormat.format(listing.getCurrentBidAmount())
                        : "&%7開始価格: &%6" + MoneyFormat.format(listing.getPrice()) + " &%7(入札なし)")
                : "&%7価格: &%6" + MoneyFormat.format(listing.getPrice());
        String actionLine = own
                ? "&%cクリックでキャンセル"
                : bidListing ? "&%aクリックで入札 (&%6" + MoneyFormat.format(auctionService.minimumNextBid(listing)) + "&%a〜)" : "&%aクリックで購入";
        return List.of("&%7出品者: &%f" + listing.getSellerName(), priceLine, actionLine);
    }

    /**
     * Builds {@code auction.bought} as a Component instead of going through
     * {@link MessageManager#send} - the item-name portion needs a {@code HoverEvent.showItem}
     * (vanilla's own {@code [Item]} tooltip) attached, which the string-only formatter can't do.
     */
    private void sendBoughtMessage(Player clicker, AuctionListing listing) {
        String template = messages.raw("auction.bought").replace("{price}", MoneyFormat.format(listing.getPrice()));
        Component prefix = ColorUtil.component(messages.getPrefix());
        int itemStart = template.indexOf("{item}");
        if (itemStart < 0) {
            clicker.sendMessage(prefix.append(ColorUtil.component(template)));
            return;
        }
        String before = template.substring(0, itemStart);
        String after = template.substring(itemStart + "{item}".length());
        clicker.sendMessage(prefix.append(ColorUtil.component(before))
                .append(ColorUtil.componentWithItemHover(listing.getDisplayName(), listing.getItem()))
                .append(ColorUtil.component(after)));
    }
}
