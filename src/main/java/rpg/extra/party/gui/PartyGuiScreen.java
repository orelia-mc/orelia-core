package rpg.extra.party.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import rpg.core.chat.ChatInputService;
import rpg.core.message.MessageManager;
import rpg.extra.party.model.Party;
import rpg.extra.party.service.PartyService;
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
 * GUI counterpart of every {@code /party} subcommand - a party has no browsable "list of all
 * parties" the way guilds do, so this screen goes straight to "my own party", or (if not in
 * one) a create button plus an accept/decline prompt if a pending invite exists. Free-text
 * fields (an invitee's name, a chat message) are collected via {@link ChatInputService} - typed
 * in chat after a prompt, not a suggest-command prefill - and every action dispatches through
 * {@link Player#performCommand} to the real {@code /party ...} command, same reasoning as
 * {@link rpg.extra.guild.gui.GuildGuiScreen}.
 */
public final class PartyGuiScreen {

    private static final GuiPageLayout MEMBER_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final GuiPageLayout PENDING_LAYOUT = new GuiPageLayout(IntStream.range(0, 18).toArray(), 18, 26);
    private static final int CREATE_SLOT = 13;
    private static final int PENDING_INVITES_SLOT = 13;
    private static final int INVITE_SLOT = 19;
    private static final int CHAT_SLOT = 20;
    private static final int LEAVE_SLOT = 24;
    private static final int BACK_SLOT = 22;

    private final PartyService partyService;
    private final GuiManager guiManager;
    private final ChatInputService chatInput;
    private final MessageManager messages;

    public PartyGuiScreen(PartyService partyService, GuiManager guiManager, ChatInputService chatInput, MessageManager messages) {
        this.partyService = partyService;
        this.guiManager = guiManager;
        this.chatInput = chatInput;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Party party = partyService.getParty(player.getUniqueId()).orElse(null);
        return party != null ? buildRoster(player, party, 0) : buildNoParty(player);
    }

    private Gui buildNoParty(Player player) {
        Gui gui = new Gui("&%8パーティー", 27);
        int pendingCount = partyService.peekAllPendingInvites(player.getUniqueId()).size();
        if (pendingCount > 0) {
            gui.set(PENDING_INVITES_SLOT, new GuiButton(new ItemBuilder(Material.WRITTEN_BOOK)
                    .name("&%e招待が" + pendingCount + "件届いています")
                    .lore(List.of("&%7クリックして一覧を開く")).build(),
                    (clicker, clickType) -> guiManager.open(clicker, buildPendingInvites(clicker, 0))));
        } else {
            gui.set(CREATE_SLOT, new GuiButton(new ItemBuilder(Material.EMERALD).name("&%aパーティーを作成")
                    .lore(List.of("&%7クリックして作成")).build(), (clicker, clickType) -> {
                clicker.performCommand("party create");
                guiManager.open(clicker, build(clicker));
            }));
        }
        return gui;
    }

    /** Every party currently inviting the viewer, oldest first - each entry answered independently, not just the single oldest one. */
    private Gui buildPendingInvites(Player player, int page) {
        Gui gui = new Gui("&%8届いているパーティー招待", 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, build(clicker))));

        List<Party> invites = partyService.peekAllPendingInvites(player.getUniqueId());
        GuiPaginator.placePage(guiManager, gui, PENDING_LAYOUT, invites, page,
                this::pendingInviteButton, p -> buildPendingInvites(player, p));
        return gui;
    }

    private GuiButton pendingInviteButton(Party party) {
        OfflinePlayer leader = Bukkit.getOfflinePlayer(party.getLeaderId());
        String leaderName = leader.getName();
        String displayName = "&%e" + (leaderName != null ? leaderName : party.getLeaderId()) + " のパーティー";
        List<String> lore = List.of("&%a左クリックで承認", "&%c右クリックで拒否");
        return new GuiButton(GuiPlayerHead.build(leader, displayName, lore), (clicker, clickType) -> {
            if (leaderName == null) {
                return;
            }
            boolean decline = clickType != null && clickType.contains("RIGHT");
            clicker.performCommand("party " + (decline ? "decline " : "accept ") + leaderName);
            guiManager.open(clicker, build(clicker));
        });
    }

    private Gui buildRoster(Player player, Party party, int page) {
        Gui gui = new Gui("&%8パーティー", 27);
        boolean viewerIsLeader = party.getLeaderId().equals(player.getUniqueId());

        List<UUID> members = List.copyOf(party.getMembers());
        GuiPaginator.placePage(guiManager, gui, MEMBER_LAYOUT, members, page,
                memberId -> memberButton(party, memberId, viewerIsLeader),
                p -> buildRoster(player, party, p));

        if (viewerIsLeader) {
            gui.set(INVITE_SLOT, new GuiButton(new ItemBuilder(Material.NAME_TAG).name("&%b招待")
                    .lore(List.of("&%7クリックしてプレイヤー名をチャットで入力")).build(), (clicker, clickType) -> {
                clicker.closeInventory();
                messages.send(clicker, "party.invite-prompt-player");
                chatInput.request(clicker, name -> {
                    clicker.performCommand("party invite " + name);
                    guiManager.open(clicker, build(clicker));
                });
            }));
        }

        // Switches the clicker's currently-selected chat channel to party (ChatChannelCommand /
        // ChatChannelService#switchChannel) rather than prompting for a one-off message - every
        // ordinary chat line typed afterward routes to party chat until switched again.
        gui.set(CHAT_SLOT, new GuiButton(new ItemBuilder(Material.WRITABLE_BOOK).name("&%bパーティーチャットに切り替え")
                .lore(List.of("&%7クリックしてチャットの送信先をパーティーに切り替える")).build(), (clicker, clickType) -> {
            clicker.performCommand("chat party");
            guiManager.open(clicker, build(clicker));
        }));

        String leaveLabel = viewerIsLeader ? "&%c解散" : "&%c脱退";
        String leaveHint = viewerIsLeader ? "&%7クリックしてパーティーを解散" : "&%7クリックしてパーティーを脱退";
        gui.set(LEAVE_SLOT, new GuiButton(new ItemBuilder(Material.BARRIER).name(leaveLabel)
                .lore(List.of(leaveHint)).build(), (clicker, clickType) -> {
            clicker.performCommand(viewerIsLeader ? "party disband" : "party leave");
            guiManager.open(clicker, build(clicker));
        }));
        return gui;
    }

    private GuiButton memberButton(Party party, UUID memberId, boolean viewerIsLeader) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
        String name = offline.getName();
        boolean online = offline.isOnline();
        boolean isLeader = memberId.equals(party.getLeaderId());
        boolean canManage = viewerIsLeader && !isLeader && name != null;
        String displayName = (online ? "&%a" : "&%7") + (name != null ? name : memberId) + (isLeader ? " &%6[リーダー]" : "");
        List<String> lore = new ArrayList<>();
        if (canManage) {
            lore.add("&%eクリックして管理");
        }
        return new GuiButton(GuiPlayerHead.build(offline, displayName, lore), (clicker, clickType) -> {
            if (canManage) {
                guiManager.open(clicker, buildMemberActions(clicker, name));
            }
        });
    }

    private Gui buildMemberActions(Player viewer, String targetName) {
        Gui gui = new Gui("&%8メンバー操作 - " + targetName, 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, build(clicker))));
        gui.set(11, memberActionButton("&%6リーダー権限を譲渡", "party transfer " + targetName));
        gui.set(15, memberActionButton("&%c追放", "party kick " + targetName));
        return gui;
    }

    private GuiButton memberActionButton(String label, String command) {
        return new GuiButton(new ItemBuilder(Material.PAPER).name(label).build(), (clicker, clickType) -> {
            clicker.performCommand(command);
            guiManager.open(clicker, build(clicker));
        });
    }
}
