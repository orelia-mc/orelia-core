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
 * List/buy/cancel/collect flow for the player-run auction house (SOW AuctionModule). Money
 * moves through Vault's {@link Economy}; orelia-extra never touches orelia-core's economy
 * internals directly.
 */
public final class AuctionService {

    public enum ActionResult {
        OK, NOT_FOUND, ALREADY_RESOLVED, NOT_OWNER, CANNOT_BUY_OWN, INSUFFICIENT_FUNDS, INVALID_PRICE, EMPTY_HAND,
        INVENTORY_FULL, MAX_LISTINGS_REACHED;

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

    /** Expired (unsold) listings a seller can reclaim their item from. Sales settle instantly at buy time. */
    public List<AuctionListing> getCollectable(UUID playerId) {
        List<AuctionListing> collectable = new ArrayList<>();
        for (AuctionListing listing : listingsById.values()) {
            if (listing.getSellerId().equals(playerId) && listing.getStatus() == AuctionListing.Status.EXPIRED) {
                collectable.add(listing);
            }
        }
        return collectable;
    }

    /** Uses {@link AuctionConfig#getDefaultDurationMillis()} rather than a caller-supplied duration. */
    public ActionResult list(Player seller, double price) {
        return list(seller, price, config.getDefaultDurationMillis());
    }

    public ActionResult list(Player seller, double price, long durationMillis) {
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
                toList, price, now, now + durationMillis, AuctionListing.Status.ACTIVE, null);
        listingsById.put(listing.getId(), listing);
        repository.save(listing);
        return ActionResult.OK;
    }

    public ActionResult buy(Player buyer, UUID listingId) {
        AuctionListing listing = listingsById.get(listingId);
        if (listing == null) {
            return ActionResult.NOT_FOUND;
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
     * Marks any listing past its expiry as EXPIRED so the seller can collect it back, and
     * mails them a heads-up - previously only a successful sale sent mail, so an unsold
     * listing just silently sat there until the seller happened to check the auction GUI.
     * Call periodically.
     */
    public void expireOverdueListings() {
        for (AuctionListing listing : listingsById.values()) {
            if (listing.getStatus() == AuctionListing.Status.ACTIVE && listing.isExpiredByTime()) {
                listing.setStatus(AuctionListing.Status.EXPIRED);
                repository.save(listing);
                String itemName = listing.getDisplayName();
                String subject = messages.format("auction.expired-mail-subject", "item", itemName);
                String body = messages.format("auction.expired-mail-body", "item", itemName);
                mailService.send(listing.getSellerId(), null, subject, body);
            }
        }
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
