package rpg.extra.party.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import rpg.extra.party.model.Party;
import rpg.extra.party.service.PartyService;
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
 * GUI counterpart of {@code /ol party list} - same shape as {@link rpg.extra.guild.gui.GuildGuiScreen}'s
 * detail screen (a party has no browsable "list of all parties" the way guilds do, so this
 * screen goes straight to "my own party" or, if the viewer isn't in one, a single "パーティーを
 * 作成" button). Actions that need a target player name (invite, kick) can't take free text
 * through an inventory click, so those buttons close the screen and hand the player a
 * suggest-command chat line instead, same pattern {@link rpg.extra.guild.gui.GuildGuiScreen}
 * uses for guild creation. Every action button delegates to {@code /party ...} via
 * {@link Player#performCommand} rather than calling {@link PartyService} directly, so the
 * existing command's messaging/sound-notification/broadcast side effects fire exactly as they
 * would from chat - this screen only builds a friendlier way to trigger them.
 */
public final class PartyGuiScreen {

    private static final GuiPageLayout MEMBER_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final int CREATE_SLOT = 13;
    private static final int INVITE_SLOT = 20;
    private static final int LEAVE_SLOT = 24;

    private final PartyService partyService;
    private final GuiManager guiManager;

    public PartyGuiScreen(PartyService partyService, GuiManager guiManager) {
        this.partyService = partyService;
        this.guiManager = guiManager;
    }

    public Gui build(Player player) {
        Party party = partyService.getParty(player.getUniqueId()).orElse(null);
        return party != null ? buildRoster(player, party, 0) : buildNoParty();
    }

    private Gui buildNoParty() {
        Gui gui = new Gui("&%8パーティー", 27);
        gui.set(CREATE_SLOT, new GuiButton(new ItemBuilder(Material.EMERALD).name("&%aパーティーを作成")
                .lore(List.of("&%7クリックして作成")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            clicker.performCommand("party create");
        }));
        return gui;
    }

    private Gui buildRoster(Player player, Party party, int page) {
        Gui gui = new Gui("&%8パーティー", 27);
        boolean viewerIsLeader = party.getLeaderId().equals(player.getUniqueId());

        List<UUID> members = List.copyOf(party.getMembers());
        GuiPaginator.placePage(guiManager, gui, MEMBER_LAYOUT, members, page,
                memberId -> memberButton(memberId, party, viewerIsLeader),
                p -> buildRoster(player, party, p));

        gui.set(INVITE_SLOT, new GuiButton(new ItemBuilder(Material.NAME_TAG).name("&%b招待")
                .lore(List.of("&%7クリックしてプレイヤー名を入力")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            clicker.sendMessage(ColorUtil.componentWithSuggestCommand(
                    "&%bクリックして招待するプレイヤー名を入力: /party invite ", "/party invite "));
        }));

        String leaveLabel = viewerIsLeader ? "&%c解散" : "&%c脱退";
        String leaveHint = viewerIsLeader ? "&%7クリックしてパーティーを解散" : "&%7クリックしてパーティーを脱退";
        gui.set(LEAVE_SLOT, new GuiButton(new ItemBuilder(Material.BARRIER).name(leaveLabel)
                .lore(List.of(leaveHint)).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            clicker.performCommand(viewerIsLeader ? "party disband" : "party leave");
        }));
        return gui;
    }

    private GuiButton memberButton(UUID memberId, Party party, boolean viewerIsLeader) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
        String name = offline.getName();
        boolean online = offline.isOnline();
        boolean isLeader = memberId.equals(party.getLeaderId());
        String displayName = (online ? "&%a" : "&%7") + (name != null ? name : memberId) + (isLeader ? " &%6[リーダー]" : "");
        boolean kickable = viewerIsLeader && !isLeader;
        List<String> lore = new ArrayList<>();
        if (kickable) {
            lore.add("&%cShift+クリックで追放");
        }
        return new GuiButton(GuiPlayerHead.build(offline, displayName, lore), (clicker, clickType) -> {
            if (kickable && clickType != null && clickType.startsWith("SHIFT_") && name != null) {
                clicker.closeInventory();
                clicker.performCommand("party kick " + name);
            }
        });
    }
}
