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

/**
 * GUI counterpart of {@code /ol friend}'s subcommands - a flat, paginated friend list (no
 * list -> detail drill-down the way guild has, since a friend entry has nothing further to
 * show) plus a pending-incoming-request prompt when one exists. A plain click sends a teleport
 * request to an online friend (same as clicking chat's own {@code friend.tp-button}); shift-click
 * removes the friend. {@code /friend add <player>} needs a free-text player name, collected via
 * {@link ChatInputService} (typed in chat after a prompt, not a suggest-command prefill) same
 * as {@link rpg.extra.guild.gui.GuildGuiScreen}/{@link rpg.extra.party.gui.PartyGuiScreen}.
 * Every action dispatches through {@link Player#performCommand} to the real {@code /friend ...}
 * command, not {@link FriendService} directly, so messaging/notifications fire exactly as they
 * already do from chat.
 */
public final class FriendGuiScreen {

    private static final GuiPageLayout LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final int ADD_SLOT = 22;
    private static final int REQUEST_ACCEPT_SLOT = 2;
    private static final int REQUEST_DECLINE_SLOT = 6;

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

        UUID requesterId = friendService.peekPendingRequester(player.getUniqueId()).orElse(null);
        if (requesterId != null) {
            String requesterName = Bukkit.getOfflinePlayer(requesterId).getName();
            gui.set(REQUEST_ACCEPT_SLOT, requestResponseButton(true, requesterName));
            gui.set(REQUEST_DECLINE_SLOT, requestResponseButton(false, requesterName));
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

    private GuiButton requestResponseButton(boolean accept, String requesterName) {
        Material material = accept ? Material.LIME_DYE : Material.RED_DYE;
        String label = (accept ? "&%a申請を承認: " : "&%c申請を拒否: ") + requesterName;
        return new GuiButton(new ItemBuilder(material).name(label).build(), (clicker, clickType) -> {
            clicker.performCommand(accept ? "friend accept" : "friend decline");
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
