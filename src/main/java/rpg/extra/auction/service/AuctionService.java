package rpg.extra.auction.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
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
        OK, NOT_FOUND, ALREADY_RESOLVED, NOT_OWNER, CANNOT_BUY_OWN, INSUFFICIENT_FUNDS, INVALID_PRICE, EMPTY_HAND, INVENTORY_FULL;

        /** {@code messages.yml} key for this result's human-readable reason (see {@code auction.reason.*}) - never show the raw enum name to a player. */
        public String reasonMessageKey() {
            return "auction.reason." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    private final AuctionRepository repository;
    private final Economy economy;
    private final MailService mailService;
    private final MessageManager messages;
    private final Map<UUID, AuctionListing> listingsById = new ConcurrentHashMap<>();

    public AuctionService(AuctionRepository repository, Economy economy, MailService mailService, MessageManager messages) {
        this.repository = repository;
        this.economy = economy;
        this.mailService = mailService;
        this.messages = messages;
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

    public ActionResult list(Player seller, double price, long durationMillis) {
        if (price <= 0) {
            return ActionResult.INVALID_PRICE;
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
        economy.withdrawPlayer(buyer, listing.getPrice());
        economy.depositPlayer(Bukkit.getOfflinePlayer(listing.getSellerId()), listing.getPrice());

        String itemName = listing.getDisplayName();
        String subject = messages.format("auction.sold-mail-subject", "item", itemName);
        String body = messages.format("auction.sold-mail-body", "item", itemName, "price", listing.getPrice(), "buyer", buyer.getName());
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

    /** Marks any listing past its expiry as EXPIRED so the seller can collect it back. Call periodically. */
    public void expireOverdueListings() {
        for (AuctionListing listing : listingsById.values()) {
            if (listing.getStatus() == AuctionListing.Status.ACTIVE && listing.isExpiredByTime()) {
                listing.setStatus(AuctionListing.Status.EXPIRED);
                repository.save(listing);
            }
        }
    }
}
