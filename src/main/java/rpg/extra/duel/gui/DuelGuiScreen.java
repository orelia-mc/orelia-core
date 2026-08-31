package rpg.extra.duel.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.duel.manager.DuelRequestManager;
import rpg.extra.duel.service.DuelService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiPlayerHead;

import java.util.List;
import java.util.UUID;

/** Every player currently requesting a duel with the viewer, oldest first - mirrors rpg.extra.friend.gui.FriendGuiScreen's own pending-request list shape. */
public final class DuelGuiScreen {

    private final DuelRequestManager requestManager;
    private final DuelService duelService;
    private final MessageManager messages;

    public DuelGuiScreen(DuelRequestManager requestManager, DuelService duelService, MessageManager messages) {
        this.requestManager = requestManager;
        this.duelService = duelService;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8届いている決闘申請", 27);
        List<UUID> requesters = requestManager.peekAll(player.getUniqueId());
        if (requesters.isEmpty()) {
            gui.set(13, GuiButton.display(new rpg.util.ItemBuilder(Material.PAPER).name("&%7決闘申請はありません").build()));
            return gui;
        }
        int slot = 0;
        for (UUID requesterId : requesters) {
            if (slot >= 27) {
                break;
            }
            gui.set(slot++, requestButton(player, requesterId));
        }
        return gui;
    }

    private GuiButton requestButton(Player target, UUID requesterId) {
        OfflinePlayer requester = Bukkit.getOfflinePlayer(requesterId);
        String name = requester.getName();
        String displayName = "&%e" + (name != null ? name : requesterId) + " からの決闘申請";
        List<String> lore = List.of("&%a左クリックで承認", "&%c右クリックで拒否");
        return new GuiButton(GuiPlayerHead.build(requester, displayName, lore), (clicker, clickType) -> {
            boolean decline = clickType != null && clickType.contains("RIGHT");
            clicker.closeInventory();
            if (decline) {
                duelService.decline(clicker, requesterId);
                messages.send(clicker, "duel.declined");
                Player requesterPlayer = Bukkit.getPlayer(requesterId);
                if (requesterPlayer != null) {
                    messages.send(requesterPlayer, "duel.declined-notice", "player", clicker.getName());
                }
                return;
            }
            Player requesterPlayer = Bukkit.getPlayer(requesterId);
            if (requesterPlayer == null) {
                messages.send(clicker, "duel.requester-offline");
                return;
            }
            DuelService.AcceptResult result = duelService.accept(clicker, requesterId, id -> java.util.Optional.ofNullable(Bukkit.getPlayer(id)));
            switch (result) {
                case OK, NO_ARENA_FREE -> { } // DuelService.accept already messaged both participants
                case NO_PENDING_REQUEST -> messages.send(clicker, "duel.no-pending-request");
                case ALREADY_IN_DUEL -> messages.send(clicker, "duel.already-in-duel");
            }
        });
    }
}
