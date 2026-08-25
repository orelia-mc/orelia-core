package rpg.extra.friend.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import rpg.core.chat.ChatInputService;
import rpg.core.message.MessageManager;
import rpg.extra.friend.service.FriendService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.gui.framework.GuiPageLayout;
import rpg.gui.framework.GuiPaginator;
import rpg.gui.framework.GuiPlayerHead;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * GUI counterpart of {@code /ol friend}'s subcommands - a flat, paginated friend list (no
 * list -> detail drill-down the way guild has, since a friend entry has nothing further to
 * show) plus a summary button opening the pending-incoming-requests list when one or more
 * exist. A plain click sends a teleport request to an online friend (same as clicking chat's own
 * {@code friend.tp-button}); shift-click removes the friend. {@code /friend add <player>} needs
 * a free-text player name, collected via {@link ChatInputService} (typed in chat after a prompt,
 * not a suggest-command prefill) same as {@link rpg.extra.guild.gui.GuildGuiScreen}/
 * {@link rpg.extra.party.gui.PartyGuiScreen}. Every action dispatches through
 * {@link Player#performCommand} to the real {@code /friend ...} command, not {@link FriendService}
 * directly, so messaging/notifications fire exactly as they already do from chat.
 */
public final class FriendGuiScreen {

    private static final GuiPageLayout LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final GuiPageLayout PENDING_LAYOUT = new GuiPageLayout(IntStream.range(0, 18).toArray(), 18, 26);
    private static final int ADD_SLOT = 22;
    private static final int PENDING_REQUESTS_SLOT = 4;

    private final FriendService friendService;
    private final GuiManager guiManager;
    private final ChatInputService chatInput;
    private final MessageManager messages;

    public FriendGuiScreen(FriendService friendService, GuiManager guiManager, ChatInputService chatInput, MessageManager messages) {
        this.friendService = friendService;
        this.guiManager = guiManager;
        this.chatInput = chatInput;
        this.messages = messages;
    }

    public Gui build(Player player) {
        return build(player, 0);
    }

    private Gui build(Player player, int page) {
        Gui gui = new Gui("&%8フレンド一覧", 27);

        int pendingCount = friendService.peekAllPendingRequesters(player.getUniqueId()).size();
        if (pendingCount > 0) {
            gui.set(PENDING_REQUESTS_SLOT, pendingRequestsButton(pendingCount));
        }

        gui.set(ADD_SLOT, new GuiButton(new ItemBuilder(Material.EMERALD).name("&%aフレンド追加")
                .lore(List.of("&%7クリックしてプレイヤー名をチャットで入力")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            messages.send(clicker, "friend.add-prompt-player");
            chatInput.request(clicker, name -> clicker.performCommand("friend add " + name));
        }));

        List<UUID> friends = List.copyOf(friendService.listFriends(player.getUniqueId()));
        GuiPaginator.placePage(guiManager, gui, LAYOUT, friends, page,
                this::friendButton, p -> build(player, p));
        return gui;
    }

    private GuiButton pendingRequestsButton(int count) {
        return new GuiButton(new ItemBuilder(Material.WRITTEN_BOOK).name("&%e申請が" + count + "件届いています")
                .lore(List.of("&%7クリックして一覧を開く")).build(),
                (clicker, clickType) -> guiManager.open(clicker, buildPendingRequests(clicker, 0)));
    }

    /** Every requester currently queued, oldest first (matches {@code /friend accept} with no argument accepting the same one) - each entry answered independently, not just the single oldest one. */
    private Gui buildPendingRequests(Player player, int page) {
        Gui gui = new Gui("&%8届いているフレンド申請", 27);
        gui.set(22, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, build(clicker, 0))));

        List<UUID> requesters = friendService.peekAllPendingRequesters(player.getUniqueId());
        GuiPaginator.placePage(guiManager, gui, PENDING_LAYOUT, requesters, page,
                this::pendingRequestButton, p -> buildPendingRequests(player, p));
        return gui;
    }

    private GuiButton pendingRequestButton(UUID requesterId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(requesterId);
        String name = offline.getName();
        String displayName = "&%e" + (name != null ? name : requesterId);
        List<String> lore = List.of("&%a左クリックで承認", "&%c右クリックで拒否");
        return new GuiButton(GuiPlayerHead.build(offline, displayName, lore), (clicker, clickType) -> {
            if (name == null) {
                return;
            }
            boolean decline = clickType != null && clickType.contains("RIGHT");
            clicker.performCommand("friend " + (decline ? "decline " : "accept ") + name);
            guiManager.open(clicker, build(clicker, 0));
        });
    }

    private GuiButton friendButton(UUID friendId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(friendId);
        String name = offline.getName();
        boolean online = offline.isOnline();
        String displayName = (online ? "&%a" : "&%7") + (name != null ? name : friendId);
        List<String> lore = new ArrayList<>();
        if (online) {
            lore.add("&%7クリックしてテレポート申請");
        }
        lore.add("&%cShift+クリックでフレンド解除");
        return new GuiButton(GuiPlayerHead.build(offline, displayName, lore), (clicker, clickType) -> {
            if (name == null) {
                return;
            }
            if (clickType != null && clickType.startsWith("SHIFT_")) {
                clicker.performCommand("friend remove " + name);
                guiManager.open(clicker, build(clicker, 0));
            } else if (online) {
                clicker.performCommand("friend tpa " + name);
            }
        });
    }
}
