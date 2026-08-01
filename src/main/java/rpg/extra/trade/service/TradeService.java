package rpg.extra.trade.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.extra.trade.manager.TradeManager;
import rpg.extra.trade.model.TradeOffer;
import rpg.extra.trade.model.TradeSession;

import java.util.Optional;
import java.util.UUID;

/**
 * Two-player item trade (SOW TradeModule). Deliberately command-driven rather than a
 * shared GUI: each side adds/removes items from their own offer with
 * {@code /ol trade add|remove}, then both must {@code /ol trade confirm} before anything moves.
 * Items leave the offering player's inventory the moment they're added (so a player can't
 * offer an item and still use/drop it), and are returned immediately on cancel.
 */
public final class TradeService {

    public enum ActionResult {
        OK, ALREADY_TRADING, NOT_TRADING, NO_PENDING_REQUEST, CANNOT_TARGET_SELF, EMPTY_HAND, INVALID_SLOT
    }

    private final TradeManager manager;

    public TradeService(TradeManager manager) {
        this.manager = manager;
    }

    public ActionResult request(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return ActionResult.CANNOT_TARGET_SELF;
        }
        if (manager.getByPlayer(requester.getUniqueId()).isPresent() || manager.getByPlayer(target.getUniqueId()).isPresent()) {
            return ActionResult.ALREADY_TRADING;
        }
        manager.requestTrade(requester.getUniqueId(), target.getUniqueId());
        return ActionResult.OK;
    }

    public ActionResult accept(Player target) {
        Optional<UUID> requesterId = manager.consumeRequest(target.getUniqueId());
        if (requesterId.isEmpty()) {
            return ActionResult.NO_PENDING_REQUEST;
        }
        manager.start(requesterId.get(), target.getUniqueId());
        return ActionResult.OK;
    }

    public ActionResult addHeldItem(Player player) {
        TradeSession session = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (session == null) {
            return ActionResult.NOT_TRADING;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return ActionResult.EMPTY_HAND;
        }
        session.offerOf(player.getUniqueId()).addItem(held.clone());
        player.getInventory().setItemInMainHand(null);
        return ActionResult.OK;
    }

    public ActionResult removeOfferedItem(Player player, int index) {
        TradeSession session = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (session == null) {
            return ActionResult.NOT_TRADING;
        }
        ItemStack removed = session.offerOf(player.getUniqueId()).removeItem(index);
        if (removed == null) {
            return ActionResult.INVALID_SLOT;
        }
        giveOrDrop(player, removed);
        return ActionResult.OK;
    }

    /** Returns true once both sides have confirmed and the trade has been executed. */
    public boolean confirm(Player player) {
        TradeSession session = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (session == null) {
            return false;
        }
        session.offerOf(player.getUniqueId()).setConfirmed(true);
        if (session.bothConfirmed()) {
            execute(session);
            return true;
        }
        return false;
    }

    public ActionResult cancel(Player player) {
        TradeSession session = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (session == null) {
            return ActionResult.NOT_TRADING;
        }
        returnItems(session);
        manager.end(session);
        return ActionResult.OK;
    }

    public Optional<TradeSession> getSession(UUID playerId) {
        return manager.getByPlayer(playerId);
    }

    /** Called on disconnect - always returns items even if the other side never sees a message. */
    public void forceCancelIfTrading(UUID playerId) {
        manager.getByPlayer(playerId).ifPresent(session -> {
            returnItems(session);
            manager.end(session);
        });
        manager.clearRequest(playerId);
    }

    private void execute(TradeSession session) {
        deliverOffer(session.getPlayerA(), session.offerOf(session.getPlayerB()));
        deliverOffer(session.getPlayerB(), session.offerOf(session.getPlayerA()));
        manager.end(session);
    }

    private void returnItems(TradeSession session) {
        deliverOffer(session.getPlayerA(), session.offerOf(session.getPlayerA()));
        deliverOffer(session.getPlayerB(), session.offerOf(session.getPlayerB()));
    }

    private void deliverOffer(UUID recipientId, TradeOffer offer) {
        Player recipient = Bukkit.getPlayer(recipientId);
        for (ItemStack item : offer.getItems()) {
            if (recipient != null && recipient.isOnline()) {
                giveOrDrop(recipient, item);
            }
        }
        offer.getItems().clear();
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
