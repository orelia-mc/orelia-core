package rpg.extra.auction.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.extra.auction.config.AuctionConfig;
import rpg.extra.auction.model.AuctionListing;
import rpg.extra.auction.repository.AuctionRepository;
import rpg.extra.mail.service.MailService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * List/buy/bid/cancel/collect flow for the player-run auction house (SOW AuctionModule). Money
 * moves through Vault's {@link Economy}; orelia-extra never touches orelia-core's economy
 * internals directly.
 */
public final class AuctionService {

    public enum ActionResult {
        OK, NOT_FOUND, ALREADY_RESOLVED, NOT_OWNER, CANNOT_BUY_OWN, INSUFFICIENT_FUNDS, INVALID_PRICE, EMPTY_HAND,
        INVENTORY_FULL, MAX_LISTINGS_REACHED,
        NOT_A_BID_LISTING, IS_A_BID_LISTING, CANNOT_BID_OWN, BID_TOO_LOW, CANNOT_CANCEL_HAS_BIDS;

        /** {@code messages.yml} key for this result's human-readable reason (see {@code auction.reason.*}) - never show the raw enum name to a player. */
        public String reasonMessageKey() {
            return "auction.reason." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    private final AuctionRepository repository;
    private final Economy economy;
    private final MailService mailService;
    private final MessageManager messages;
    private final AuctionConfig config;
    private final Map<UUID, AuctionListing> listingsById = new ConcurrentHashMap<>();

    public AuctionService(AuctionRepository repository, Economy economy, MailService mailService,
                           MessageManager messages, AuctionConfig config) {
        this.repository = repository;
        this.economy = economy;
        this.mailService = mailService;
        this.messages = messages;
        this.config = config;
    }

    public void loadAll() {
        listingsById.clear();
        for (AuctionListing listing : repository.findAllActiveOrPending()) {
            listingsById.put(listing.getId(), listing);
        }
    }

    public List<AuctionListing> getActiveListings() {
        List<AuctionListing> active = new ArrayList<>();
        for (AuctionListing listing : listingsById.values()) {
            if (listing.getStatus() == AuctionListing.Status.ACTIVE) {
                active.add(listing);
            }
        }
        active.sort(Comparator.comparingLong(AuctionListing::getListedAtMillis).reversed());
        return active;
    }

    /** Expired (unsold, or a bid auction that closed with no bids) listings a seller can reclaim their item from. A buy-now sale / a settled bid auction both settle instantly, never landing here. */
    public List<AuctionListing> getCollectable(UUID playerId) {
        List<AuctionListing> collectable = new ArrayList<>();
        for (AuctionListing listing : listingsById.values()) {
            if (listing.getSellerId().equals(playerId) && listing.getStatus() == AuctionListing.Status.EXPIRED) {
                collectable.add(listing);
            }
        }
        return collectable;
    }

    /** Flat-price instant-buy listing, using {@link AuctionConfig#getDefaultDurationMillis()}. */
    public ActionResult list(Player seller, double price) {
        return list(seller, price, config.getDefaultDurationMillis());
    }

    public ActionResult list(Player seller, double price, long durationMillis) {
        return createListing(seller, price, durationMillis, AuctionListing.ListingType.BUY_NOW);
    }

    /** Timed bid auction starting at {@code startPrice}, using {@link AuctionConfig#getDefaultDurationMillis()}. */
    public ActionResult startAuction(Player seller, double startPrice) {
        return startAuction(seller, startPrice, config.getDefaultDurationMillis());
    }

    public ActionResult startAuction(Player seller, double startPrice, long durationMillis) {
        return createListing(seller, startPrice, durationMillis, AuctionListing.ListingType.BID);
    }

    private ActionResult createListing(Player seller, double price, long durationMillis, AuctionListing.ListingType type) {
        if (price <= 0) {
            return ActionResult.INVALID_PRICE;
        }
        if (countActiveOrPendingBySeller(seller.getUniqueId()) >= config.getMaxListingsPerSeller()) {
            return ActionResult.MAX_LISTINGS_REACHED;
        }
        ItemStack held = seller.getInventory().getItemInMainHand();
        if (held.getType().isAir() || held.getAmount() <= 0) {
            return ActionResult.EMPTY_HAND;
        }
        ItemStack toList = held.clone();
        seller.getInventory().setItemInMainHand(null);

        long now = System.currentTimeMillis();
        AuctionListing listing = new AuctionListing(UUID.randomUUID(), seller.getUniqueId(), seller.getName(),
                toList, type, price, now, now + durationMillis, AuctionListing.Status.ACTIVE, null,
                null, null, null);
        listingsById.put(listing.getId(), listing);
        repository.save(listing);
        return ActionResult.OK;
    }

    /**
     * The minimum amount a new bid on {@code listing} must meet. With no bid yet, the starting
     * price itself; otherwise the current highest bid increased by
     * {@link AuctionConfig#getBidMinIncrementRate()}. Shared by the command and the GUI's
     * quick-bid so the two never compute this differently.
     */
    public double minimumNextBid(AuctionListing listing) {
        Double currentBid = listing.getCurrentBidAmount();
        return currentBid == null ? listing.getPrice() : currentBid * (1 + config.getBidMinIncrementRate());
    }

    /**
     * Places a bid, escrowing the bidder's funds immediately (withdrawn now, refunded to the
     * previous highest bidder via {@link Economy#depositPlayer(org.bukkit.OfflinePlayer, double)}
     * the instant they're outbid - same call shape {@link #buy} already uses for seller payout,
     * just a different recipient). Chosen over a check-only-at-bid-time/withdraw-at-close design
     * because that would risk a winning bidder no longer having the funds by the time the
     * auction closes (possibly minutes to days later), with no clean recovery once the seller's
     * item is already committed to them.
     */
    public ActionResult bid(Player bidder, UUID listingId, double amount) {
        AuctionListing listing = listingsById.get(listingId);
        if (listing == null) {
            return ActionResult.NOT_FOUND;
        }
        if (listing.getType() != AuctionListing.ListingType.BID) {
            return ActionResult.NOT_A_BID_LISTING;
        }
        if (listing.getStatus() != AuctionListing.Status.ACTIVE || listing.isExpiredByTime()) {
            return ActionResult.ALREADY_RESOLVED;
        }
        if (listing.getSellerId().equals(bidder.getUniqueId())) {
            return ActionResult.CANNOT_BID_OWN;
        }
        if (amount < minimumNextBid(listing)) {
            return ActionResult.BID_TOO_LOW;
        }
        if (!economy.has(bidder, amount)) {
            return ActionResult.INSUFFICIENT_FUNDS;
        }
        UUID previousBidderId = listing.getCurrentBidderId();
        Double previousBidAmount = listing.getCurrentBidAmount();

        economy.withdrawPlayer(bidder, amount);
        if (previousBidderId != null) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(previousBidderId), previousBidAmount);
            String itemName = listing.getDisplayName();
            mailService.send(previousBidderId, null,
                    messages.format("auction.outbid-mail-subject", "item", itemName),
                    messages.format("auction.outbid-mail-body", "item", itemName, "amount", amount));
        }

        listing.setCurrentBidderId(bidder.getUniqueId());
        listing.setCurrentBidderName(bidder.getName());
        listing.setCurrentBidAmount(amount);
        repository.save(listing);
        return ActionResult.OK;
    }

    public ActionResult buy(Player buyer, UUID listingId) {
        AuctionListing listing = listingsById.get(listingId);
        if (listing == null) {
            return ActionResult.NOT_FOUND;
        }
        if (listing.getType() == AuctionListing.ListingType.BID) {
            return ActionResult.IS_A_BID_LISTING;
        }
        if (listing.getStatus() != AuctionListing.Status.ACTIVE) {
            return ActionResult.ALREADY_RESOLVED;
        }
        if (listing.getSellerId().equals(buyer.getUniqueId())) {
            return ActionResult.CANNOT_BUY_OWN;
        }
        if (!economy.has(buyer, listing.getPrice())) {
            return ActionResult.INSUFFICIENT_FUNDS;
        }
        double fee = listing.getPrice() * config.getFeeRate();
        double net = listing.getPrice() - fee;
        economy.withdrawPlayer(buyer, listing.getPrice());
        // The fee is sunk (not deposited anywhere) - a deliberate money sink rather than
        // routing it to an "operator account" that doesn't exist in this economy model.
        economy.depositPlayer(Bukkit.getOfflinePlayer(listing.getSellerId()), net);

        String itemName = listing.getDisplayName();
        String subject = messages.format("auction.sold-mail-subject", "item", itemName);
        String body = messages.format("auction.sold-mail-body", "item", itemName, "price", listing.getPrice(),
                "buyer", buyer.getName(), "fee", fee, "net", net);
        mailService.send(listing.getSellerId(), null, subject, body);

        if (!buyer.getInventory().addItem(listing.getItem().clone()).isEmpty()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), listing.getItem().clone());
        }
        listing.setBuyerId(buyer.getUniqueId());
        listing.setStatus(AuctionListing.Status.COLLECTED);
        repository.save(listing);
        return ActionResult.OK;
    }

    public ActionResult cancel(Player seller, UUID listingId) {
        AuctionListing listing = listingsById.get(listingId);
        if (listing == null) {
            return ActionResult.NOT_FOUND;
        }
        if (!listing.getSellerId().equals(seller.getUniqueId())) {
            return ActionResult.NOT_OWNER;
        }
        if (listing.getStatus() != AuctionListing.Status.ACTIVE) {
            return ActionResult.ALREADY_RESOLVED;
        }
        // A bid listing that already has a bid can't be cancelled - simpler than a
        // refund-and-cancel path for an edge case sellers can trivially avoid (don't cancel
        // once someone's bid), and matches real-world auction-house norms.
        if (listing.getType() == AuctionListing.ListingType.BID && listing.getCurrentBidderId() != null) {
            return ActionResult.CANNOT_CANCEL_HAS_BIDS;
        }
        listing.setStatus(AuctionListing.Status.EXPIRED);
        repository.save(listing);
        return collect(seller, listingId);
    }

    public ActionResult collect(Player player, UUID listingId) {
        AuctionListing listing = listingsById.get(listingId);
        if (listing == null) {
            return ActionResult.NOT_FOUND;
        }
        if (!listing.getSellerId().equals(player.getUniqueId())) {
            return ActionResult.NOT_OWNER;
        }
        if (listing.getStatus() != AuctionListing.Status.EXPIRED) {
            return ActionResult.ALREADY_RESOLVED;
        }
        if (player.getInventory().firstEmpty() == -1) {
            return ActionResult.INVENTORY_FULL;
        }
        player.getInventory().addItem(listing.getItem().clone());
        listing.setStatus(AuctionListing.Status.COLLECTED);
        repository.save(listing);
        return ActionResult.OK;
    }

    /**
     * Marks any listing past its expiry as EXPIRED (seller can collect it back) or, for a bid
     * listing that has at least one bid, settles the sale to the highest bidder - item delivered
     * via mail attachment (there's no "collect" path for a third-party winner, only the seller's
     * own reclaim), proceeds deposited to the seller immediately, both parties mailed. Call
     * periodically.
     */
    public void expireOverdueListings() {
        for (AuctionListing listing : listingsById.values()) {
            if (listing.getStatus() == AuctionListing.Status.ACTIVE && listing.isExpiredByTime()) {
                if (listing.getType() == AuctionListing.ListingType.BID && listing.getCurrentBidderId() != null) {
                    settleBidAuction(listing);
                } else {
                    expireUnsold(listing);
                }
            }
        }
    }

    private void expireUnsold(AuctionListing listing) {
        listing.setStatus(AuctionListing.Status.EXPIRED);
        repository.save(listing);
        String itemName = listing.getDisplayName();
        String subject = messages.format("auction.expired-mail-subject", "item", itemName);
        String body = messages.format("auction.expired-mail-body", "item", itemName);
        mailService.send(listing.getSellerId(), null, subject, body);
    }

    private void settleBidAuction(AuctionListing listing) {
        double amount = listing.getCurrentBidAmount();
        double fee = amount * config.getFeeRate();
        double net = amount - fee;
        economy.depositPlayer(Bukkit.getOfflinePlayer(listing.getSellerId()), net);

        String itemName = listing.getDisplayName();
        mailService.send(listing.getSellerId(), null,
                messages.format("auction.sold-mail-subject", "item", itemName),
                messages.format("auction.sold-mail-body", "item", itemName, "price", amount,
                        "buyer", listing.getCurrentBidderName(), "fee", fee, "net", net));
        mailService.send(listing.getCurrentBidderId(), null,
                messages.format("auction.won-mail-subject", "item", itemName),
                messages.format("auction.won-mail-body", "item", itemName, "price", amount),
                listing.getItem().clone());

        listing.setBuyerId(listing.getCurrentBidderId());
        listing.setStatus(AuctionListing.Status.COLLECTED);
        repository.save(listing);
    }

    /** Listings still occupying one of the seller's {@link AuctionConfig#getMaxListingsPerSeller()} slots - ACTIVE (unsold) or EXPIRED-but-not-yet-collected. */
    private long countActiveOrPendingBySeller(UUID sellerId) {
        return listingsById.values().stream()
                .filter(listing -> listing.getSellerId().equals(sellerId))
                .filter(listing -> listing.getStatus() == AuctionListing.Status.ACTIVE
                        || listing.getStatus() == AuctionListing.Status.EXPIRED)
                .count();
    }
}
