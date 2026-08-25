package rpg.extra.trade.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.core.scheduler.SchedulerService;
import rpg.extra.trade.config.TradeConfig;
import rpg.extra.trade.manager.TradeManager;
import rpg.extra.trade.model.TradeOffer;
import rpg.extra.trade.model.TradeSession;
import rpg.extra.trade.repository.TradeLogRepository;
import rpg.item.service.TradeableItemService;

import java.util.Optional;
import java.util.UUID;

/**
 * Two-player item (and, now, money) trade (SOW TradeModule). Deliberately command-driven
 * rather than a shared GUI: each side adds/removes items or sets an offered amount with
 * {@code /ol trade add|remove|money}, then both must {@code /ol trade confirm} before
 * anything moves. Items leave the offering player's inventory the moment they're added (so a
 * player can't offer an item and still use/drop it), and are returned immediately on cancel.
 */
public final class TradeService {

    public enum ActionResult {
        OK, ALREADY_TRADING, NOT_TRADING, NO_PENDING_REQUEST, CANNOT_TARGET_SELF, EMPTY_HAND, INVALID_SLOT,
        WAITING_FOR_OTHER, INSUFFICIENT_FUNDS, MONEY_UNSUPPORTED, INVALID_AMOUNT, TOO_MANY_ITEMS, ITEM_NOT_TRADEABLE
    }

    private final TradeManager manager;
    private final Economy economy;
    private final SchedulerService schedulerService;
    private final TradeConfig config;
    private final MessageManager messages;
    private final TradeLogRepository logRepository;
    private final TradeableItemService tradeableItemService;

    /** {@code economy} is nullable - Vault may not be installed, in which case offering money is rejected with {@link ActionResult#MONEY_UNSUPPORTED}. */
    public TradeService(TradeManager manager, Economy economy, SchedulerService schedulerService, TradeConfig config,
                         MessageManager messages, TradeLogRepository logRepository, TradeableItemService tradeableItemService) {
        this.manager = manager;
        this.economy = economy;
        this.schedulerService = schedulerService;
        this.config = config;
        this.messages = messages;
        this.logRepository = logRepository;
        this.tradeableItemService = tradeableItemService;
    }

    public ActionResult request(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return ActionResult.CANNOT_TARGET_SELF;
        }
        if (manager.getByPlayer(requester.getUniqueId()).isPresent() || manager.getByPlayer(target.getUniqueId()).isPresent()) {
            return ActionResult.ALREADY_TRADING;
        }
        UUID requesterId = requester.getUniqueId();
        UUID targetId = target.getUniqueId();
        manager.requestTrade(requesterId, targetId);
        schedulerService.runLater(() -> expireRequestIfStillPending(requesterId, targetId),
                config.getRequestTimeoutSeconds() * 20L);
        return ActionResult.OK;
    }

    /** Only clears the request if it's still the same one that was scheduled - a target who declined and got a new request in the meantime keeps that new one. */
    private void expireRequestIfStillPending(UUID requesterId, UUID targetId) {
        manager.consumeRequest(targetId).ifPresent(pendingRequesterId -> {
            if (pendingRequesterId.equals(requesterId)) {
                notifyIfOnline(requesterId, "trade.request-timed-out");
                notifyIfOnline(targetId, "trade.request-timed-out");
            } else {
                manager.requestTrade(targetId, pendingRequesterId);
            }
        });
    }

    public ActionResult accept(Player target) {
        Optional<UUID> requesterId = manager.consumeRequest(target.getUniqueId());
        if (requesterId.isEmpty()) {
            return ActionResult.NO_PENDING_REQUEST;
        }
        TradeSession session = manager.start(requesterId.get(), target.getUniqueId());
        scheduleSessionTimeout(session);
        return ActionResult.OK;
    }

    private void scheduleSessionTimeout(TradeSession session) {
        session.setTimeoutTask(schedulerService.runLater(() -> {
            if (manager.getByPlayer(session.getPlayerA()).filter(s -> s == session).isEmpty()) {
                return;
            }
            returnItems(session);
            manager.end(session);
            notifyIfOnline(session.getPlayerA(), "trade.session-timed-out");
            notifyIfOnline(session.getPlayerB(), "trade.session-timed-out");
        }, config.getSessionTimeoutSeconds() * 20L));
    }

    public ActionResult addHeldItem(Player player) {
        TradeSession session = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (session == null) {
            return ActionResult.NOT_TRADING;
        }
        TradeOffer offer = session.offerOf(player.getUniqueId());
        if (offer.getItems().size() >= config.getMaxItemsPerOffer()) {
            return ActionResult.TOO_MANY_ITEMS;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return ActionResult.EMPTY_HAND;
        }
        if (!tradeableItemService.isTradeable(held)) {
            return ActionResult.ITEM_NOT_TRADEABLE;
        }
        offer.addItem(held.clone());
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

    /** Sets how much money {@code player} is offering. Rejected if Vault isn't installed or the player can't cover the amount. */
    public ActionResult setOfferedMoney(Player player, double amount) {
        TradeSession session = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (session == null) {
            return ActionResult.NOT_TRADING;
        }
        if (amount < 0) {
            return ActionResult.INVALID_AMOUNT;
        }
        if (amount > 0) {
            if (economy == null) {
                return ActionResult.MONEY_UNSUPPORTED;
            }
            if (!economy.has(player, amount)) {
                return ActionResult.INSUFFICIENT_FUNDS;
            }
        }
        session.offerOf(player.getUniqueId()).setMoney(amount);
        return ActionResult.OK;
    }

    /**
     * Confirms {@code player}'s side. Once both sides have confirmed, re-checks the offered
     * money is still affordable (a player's balance can drop between offering and the other
     * side confirming) before executing - a failed check un-confirms both sides rather than
     * silently keeping one side locked in.
     */
    public ActionResult confirm(Player player) {
        TradeSession session = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (session == null) {
            return ActionResult.NOT_TRADING;
        }
        session.offerOf(player.getUniqueId()).setConfirmed(true);
        if (!session.bothConfirmed()) {
            return ActionResult.WAITING_FOR_OTHER;
        }
        if (!bothCanAffordOfferedMoney(session)) {
            session.offerOf(session.getPlayerA()).setConfirmed(false);
            session.offerOf(session.getPlayerB()).setConfirmed(false);
            return ActionResult.INSUFFICIENT_FUNDS;
        }
        execute(session);
        return ActionResult.OK;
    }

    private boolean bothCanAffordOfferedMoney(TradeSession session) {
        return canAfford(session.getPlayerA(), session.offerOf(session.getPlayerA()).getMoney())
                && canAfford(session.getPlayerB(), session.offerOf(session.getPlayerB()).getMoney());
    }

    private boolean canAfford(UUID playerId, double amount) {
        if (amount <= 0) {
            return true;
        }
        Player player = Bukkit.getPlayer(playerId);
        return economy != null && player != null && economy.has(player, amount);
    }

    public ActionResult cancel(Player player) {
        TradeSession session = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (session == null) {
            return ActionResult.NOT_TRADING;
        }
        cancelTimeout(session);
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
            cancelTimeout(session);
            returnItems(session);
            manager.end(session);
        });
        manager.clearRequest(playerId);
    }

    private void cancelTimeout(TradeSession session) {
        if (session.getTimeoutTask() != null) {
            session.getTimeoutTask().cancel();
        }
    }

    private void execute(TradeSession session) {
        cancelTimeout(session);
        transferMoney(session.getPlayerA(), session.getPlayerB(), session.offerOf(session.getPlayerA()).getMoney());
        transferMoney(session.getPlayerB(), session.getPlayerA(), session.offerOf(session.getPlayerB()).getMoney());
        logRepository.log(session);
        deliverOffer(session.getPlayerA(), session.offerOf(session.getPlayerB()));
        deliverOffer(session.getPlayerB(), session.offerOf(session.getPlayerA()));
        manager.end(session);
    }

    private void transferMoney(UUID fromId, UUID toId, double amount) {
        if (amount <= 0 || economy == null) {
            return;
        }
        Player from = Bukkit.getPlayer(fromId);
        if (from != null) {
            economy.withdrawPlayer(from, amount);
        }
        economy.depositPlayer(Bukkit.getOfflinePlayer(toId), amount);
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

    private void notifyIfOnline(UUID playerId, String messageKey) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            messages.send(player, messageKey);
        }
    }
}
