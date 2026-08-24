package rpg.extra.friend.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import rpg.extra.friend.service.FriendService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.gui.framework.GuiPageLayout;
import rpg.gui.framework.GuiPaginator;
import rpg.gui.framework.GuiPlayerHead;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI counterpart of {@code /ol friend list}'s chat listing - a flat, paginated friend list (no
 * list -> detail drill-down the way guild has, since a friend entry has nothing further to show).
 * A plain click sends a teleport request to an online friend (same as clicking chat's own
 * {@code friend.tp-button}); shift-click removes the friend. {@code /friend add <player>} needs
 * a free-text player name Minecraft's inventory UI can't take directly, so - same pattern as
 * {@link rpg.extra.guild.gui.GuildGuiScreen}'s create button - the "フレンド追加" button closes
 * the screen and hands the player a suggest-command chat line instead of a bespoke text-entry
 * screen. Every action delegates to {@code /friend ...} via {@link Player#performCommand} so
 * the existing command's messaging/notification side effects fire exactly as they would from chat.
 */
public final class FriendGuiScreen {

    private static final GuiPageLayout LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final int ADD_SLOT = 22;

    private final FriendService friendService;
    private final GuiManager guiManager;

    public FriendGuiScreen(FriendService friendService, GuiManager guiManager) {
        this.friendService = friendService;
        this.guiManager = guiManager;
    }

    public Gui build(Player player) {
        return build(player, 0);
    }

    private Gui build(Player player, int page) {
        Gui gui = new Gui("&%8フレンド一覧", 27);
        gui.set(ADD_SLOT, new GuiButton(new ItemBuilder(Material.EMERALD).name("&%aフレンド追加")
                .lore(List.of("&%7クリックしてプレイヤー名を入力")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            clicker.sendMessage(ColorUtil.componentWithSuggestCommand(
                    "&%aクリックして追加するプレイヤー名を入力: /friend add ", "/friend add "));
        }));

        List<UUID> friends = List.copyOf(friendService.listFriends(player.getUniqueId()));
        GuiPaginator.placePage(guiManager, gui, LAYOUT, friends, page,
                this::friendButton, p -> build(player, p));
        return gui;
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
                clicker.closeInventory();
                clicker.performCommand("friend remove " + name);
            } else if (online) {
                clicker.closeInventory();
                clicker.performCommand("friend tpa " + name);
            }
        });
    }
}
