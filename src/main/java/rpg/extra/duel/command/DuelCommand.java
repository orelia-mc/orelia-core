package rpg.extra.duel.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.duel.gui.DuelGuiScreen;
import rpg.extra.duel.gui.DuelRankingGuiScreen;
import rpg.extra.duel.listener.DuelDamageListener;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;
import rpg.extra.duel.service.DuelService;
import rpg.gui.framework.GuiManager;

import java.util.List;
import java.util.Optional;

/**
 * {@code /ol duel [gui|request <player>|accept [player]|decline [player]|cancel|forfeit]}.
 * Bare {@code /ol duel} (and {@code gui}) opens {@link DuelGuiScreen} - same gui-first default
 * every other general-player command in this jar now uses.
 */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("gui", "request", "accept", "decline", "cancel", "forfeit", "ranking");

    private final DuelService duelService;
    private final DuelSessionManager sessionManager;
    private final DuelDamageListener damageListener;
    private final DuelGuiScreen guiScreen;
    private final DuelRankingGuiScreen rankingScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public DuelCommand(DuelService duelService, DuelSessionManager sessionManager, DuelDamageListener damageListener,
                        DuelGuiScreen guiScreen, DuelRankingGuiScreen rankingScreen, GuiManager guiManager, MessageManager messages) {
        this.duelService = duelService;
        this.sessionManager = sessionManager;
        this.damageListener = damageListener;
        this.guiScreen = guiScreen;
        this.rankingScreen = rankingScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            guiManager.open(player, guiScreen.build(player));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "request" -> request(player, args);
            case "accept" -> accept(player, args);
            case "decline" -> decline(player, args);
            case "cancel" -> cancel(player, args);
            case "forfeit" -> forfeit(player);
            case "ranking" -> guiManager.open(player, rankingScreen.build());
            default -> messages.send(sender, "duel.usage");
        }
        return true;
    }

    private void request(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "duel.usage-request");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "command.player-not-found", "player", args[1]);
            return;
        }
        DuelService.RequestResult result = duelService.request(player, target);
        switch (result) {
            case OK -> {
                messages.send(player, "duel.request-sent", "player", target.getName());
                messages.send(target, "duel.request-received", "player", player.getName());
            }
            case ALREADY_PENDING -> messages.send(player, "duel.already-pending");
            case ON_COOLDOWN -> messages.send(player, "duel.on-cooldown");
            case SELF -> messages.send(player, "duel.cannot-target-self");
            case ALREADY_IN_DUEL -> messages.send(player, "duel.already-in-duel");
        }
    }

    private void accept(Player player, String[] args) {
        java.util.UUID requesterId = null;
        if (args.length >= 2) {
            Player requester = Bukkit.getPlayerExact(args[1]);
            if (requester == null) {
                messages.send(player, "command.player-not-found", "player", args[1]);
                return;
            }
            requesterId = requester.getUniqueId();
        }
        DuelService.AcceptResult result = duelService.accept(player, requesterId, id -> Optional.ofNullable(Bukkit.getPlayer(id)));
        switch (result) {
            case OK -> messages.send(player, "duel.started");
            case NO_ARENA_FREE -> messages.send(player, "duel.no-arena-free");
            case NO_PENDING_REQUEST -> messages.send(player, "duel.no-pending-request");
            case ALREADY_IN_DUEL -> messages.send(player, "duel.already-in-duel");
        }
    }

    private void decline(Player player, String[] args) {
        java.util.UUID requesterId = null;
        if (args.length >= 2) {
            Player requester = Bukkit.getPlayerExact(args[1]);
            requesterId = requester != null ? requester.getUniqueId() : null;
        }
        boolean declined = duelService.decline(player, requesterId);
        messages.send(player, declined ? "duel.declined" : "duel.no-pending-request");
    }

    private void cancel(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "duel.usage-cancel");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "command.player-not-found", "player", args[1]);
            return;
        }
        boolean cancelled = duelService.cancel(player, target.getUniqueId());
        messages.send(player, cancelled ? "duel.cancelled" : "duel.no-pending-request");
    }

    private void forfeit(Player player) {
        Optional<DuelSession> session = sessionManager.sessionOf(player.getUniqueId());
        if (session.isEmpty()) {
            messages.send(player, "duel.not-in-duel");
            return;
        }
        damageListener.resolveDuel(session.get(), session.get().opponentOf(player.getUniqueId()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && List.of("request", "accept", "decline", "cancel").contains(args[0].toLowerCase())) {
            return TabCompletions.onlinePlayerNames(args[1]);
        }
        return List.of();
    }
}
