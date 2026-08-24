package rpg.extra.auction.model;

import org.bukkit.inventory.ItemStack;
import rpg.extra.util.ItemDisplayNames;

import java.util.UUID;

/**
 * One item listed on the auction house (SOW AuctionModule). {@code status} tracks whether the
 * listing is still active, sold, expired (unsold, item awaiting seller pickup) or already
 * collected by the seller.
 */
public final class AuctionListing {

    public enum Status {
        ACTIVE, EXPIRED, COLLECTED
    }

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long listedAtMillis;
    private final long expiresAtMillis;
    private Status status;
    private UUID buyerId;

    public AuctionListing(UUID id, UUID sellerId, String sellerName, ItemStack item, double price,
                           long listedAtMillis, long expiresAtMillis, Status status, UUID buyerId) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.listedAtMillis = listedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.status = status;
        this.buyerId = buyerId;
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
}
