package rpg.extra.auction.model;

import org.bukkit.inventory.ItemStack;
import rpg.extra.util.ItemDisplayNames;

import java.util.UUID;

/**
 * One item listed on the auction house (SOW AuctionModule). {@code status} tracks whether the
 * listing is still active, sold, expired (unsold, item awaiting seller pickup) or already
 * collected by the seller - this two-state resolution model (ACTIVE -&gt; EXPIRED-or-COLLECTED)
 * already covers both {@link ListingType#BUY_NOW} (an instant sale jumps straight to
 * {@code COLLECTED}) and {@link ListingType#BID} (settling to the highest bidder also jumps to
 * {@code COLLECTED}; no bids at expiry falls back to the same {@code EXPIRED} seller-reclaim
 * path a buy-now listing nobody bought already uses) - no separate SOLD/WON state was needed.
 */
public final class AuctionListing {

    public enum Status {
        ACTIVE, EXPIRED, COLLECTED
    }

    /** Distinguishes a flat-price instant sale from a timed bid auction sharing this same table/model. */
    public enum ListingType {
        BUY_NOW, BID
    }

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final ListingType type;
    /** For {@link ListingType#BUY_NOW} this is the sale price; for {@link ListingType#BID} it's the starting price. */
    private final double price;
    private final long listedAtMillis;
    private final long expiresAtMillis;
    private Status status;
    private UUID buyerId;
    private UUID currentBidderId;
    private String currentBidderName;
    /** {@code null} until the first bid is placed on a {@link ListingType#BID} listing. */
    private Double currentBidAmount;

    public AuctionListing(UUID id, UUID sellerId, String sellerName, ItemStack item, ListingType type, double price,
                           long listedAtMillis, long expiresAtMillis, Status status, UUID buyerId,
                           UUID currentBidderId, String currentBidderName, Double currentBidAmount) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item;
        this.type = type;
        this.price = price;
        this.listedAtMillis = listedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.status = status;
        this.buyerId = buyerId;
        this.currentBidderId = currentBidderId;
        this.currentBidderName = currentBidderName;
        this.currentBidAmount = currentBidAmount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item;
    }

    /** A readable label for {@link #getItem()} - see {@link ItemDisplayNames#of}. */
    public String getDisplayName() {
        return ItemDisplayNames.of(item);
    }

    public ListingType getType() {
        return type;
    }

    public double getPrice() {
        return price;
    }

    public long getListedAtMillis() {
        return listedAtMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpiredByTime() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(UUID buyerId) {
        this.buyerId = buyerId;
    }

    public UUID getCurrentBidderId() {
        return currentBidderId;
    }

    public void setCurrentBidderId(UUID currentBidderId) {
        this.currentBidderId = currentBidderId;
    }

    public String getCurrentBidderName() {
        return currentBidderName;
    }

    public void setCurrentBidderName(String currentBidderName) {
        this.currentBidderName = currentBidderName;
    }

    public Double getCurrentBidAmount() {
        return currentBidAmount;
    }

    public void setCurrentBidAmount(Double currentBidAmount) {
        this.currentBidAmount = currentBidAmount;
    }
}
