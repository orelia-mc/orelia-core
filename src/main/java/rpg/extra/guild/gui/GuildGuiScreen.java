package rpg.extra.guild.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import rpg.core.chat.ChatInputService;
import rpg.core.message.MessageManager;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRole;
import rpg.extra.guild.service.GuildService;
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
import java.util.Map;
import java.util.UUID;

/**
 * GUI counterpart of every {@code /guild} subcommand - list -> detail drill-down (same shape as
 * orelia-world's {@code DungeonGuiScreen}), plus every action the command line offers: create,
 * invite, accept/decline a pending invite, leave, kick, promote, demote, transfer leadership,
 * disband, and send a guild-chat line. Free-text fields (guild name/tag, an invitee's name, a
 * chat message) are collected via {@link ChatInputService} - the player types a line in chat
 * after being prompted, rather than a suggest-command prefill they'd still have to press enter
 * on - and every action is dispatched through {@link Player#performCommand} to the real
 * {@code /guild ...} command rather than calling {@link GuildService} directly, so messaging,
 * sound notifications, and broadcasts fire exactly as they already do from chat.
 */
public final class GuildGuiScreen {

    private static final GuiPageLayout LIST_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final GuiPageLayout MEMBER_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final int BACK_SLOT = 22;
    private static final int CREATE_SLOT = 4;
    private static final int INVITE_ACCEPT_SLOT = 2;
    private static final int INVITE_DECLINE_SLOT = 6;
    private static final int INVITE_SLOT = 19;
    private static final int CHAT_SLOT = 20;
    private static final int LEAVE_SLOT = 24;

    private final GuildService guildService;
    private final GuiManager guiManager;
    private final ChatInputService chatInput;
    private final MessageManager messages;

    public GuildGuiScreen(GuildService guildService, GuiManager guiManager, ChatInputService chatInput, MessageManager messages) {
        this.guildService = guildService;
        this.guiManager = guiManager;
        this.chatInput = chatInput;
        this.messages = messages;
    }

    public Gui build(Player player) {
        return build(player, 0);
    }

    private Gui build(Player player, int page) {
        Gui gui = new Gui("&%8ギルド一覧", 27);
        Guild pendingInvite = guildService.peekPendingInvite(player.getUniqueId()).orElse(null);
        if (pendingInvite != null) {
            gui.set(INVITE_ACCEPT_SLOT, inviteResponseButton(true, pendingInvite));
            gui.set(INVITE_DECLINE_SLOT, inviteResponseButton(false, pendingInvite));
        } else if (guildService.getGuild(player.getUniqueId()).isEmpty()) {
            gui.set(CREATE_SLOT, createButton());
        }
        List<Guild> guilds = List.copyOf(guildService.getAllGuilds());
        GuiPaginator.placePage(guiManager, gui, LIST_LAYOUT, guilds, page,
                this::guildButton, p -> build(player, p));
        return gui;
    }

    private GuiButton inviteResponseButton(boolean accept, Guild guild) {
        Material material = accept ? Material.LIME_DYE : Material.RED_DYE;
        String label = accept ? "&%a招待を承認: [" + guild.getTag() + "] " + guild.getName()
                : "&%c招待を拒否: [" + guild.getTag() + "] " + guild.getName();
        return new GuiButton(new ItemBuilder(material).name(label).build(), (clicker, clickType) -> {
            clicker.performCommand(accept ? "guild accept" : "guild decline");
            guiManager.open(clicker, build(clicker, 0));
        });
    }

    /** Force-opens a specific guild's detail screen directly - the entry point {@code /ol guild gui} uses for the viewer's own guild. */
    public Gui buildDetail(Player player, UUID guildId) {
        return buildDetail(player, guildId, 0);
    }

    private Gui buildDetail(Player player, UUID guildId, int page) {
        Guild guild = guildService.getGuildById(guildId).orElse(null);
        if (guild == null) {
            return build(player, 0);
        }
        Gui gui = new Gui("&%8[" + guild.getTag() + "] " + guild.getName(), 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« ギルド一覧に戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, build(clicker, 0))));

        GuildRole viewerRole = guild.roleOf(player.getUniqueId());
        if (viewerRole != null) {
            if (viewerRole == GuildRole.LEADER || viewerRole == GuildRole.OFFICER) {
                gui.set(INVITE_SLOT, inviteButton(guildId));
            }
            gui.set(CHAT_SLOT, chatButton(guildId));
            gui.set(LEAVE_SLOT, leaveOrDisbandButton(guildId, viewerRole == GuildRole.LEADER));
        }

        List<Map.Entry<UUID, GuildRole>> members = List.copyOf(guild.getMembers().entrySet());
        GuiPaginator.placePage(guiManager, gui, MEMBER_LAYOUT, members, page,
                member -> memberButton(player, guildId, member, viewerRole), p -> buildDetail(player, guildId, p));
        return gui;
    }

    private GuiButton createButton() {
        return new GuiButton(new ItemBuilder(Material.EMERALD).name("&%aギルドを作成")
                .lore(List.of("&%7クリックして名前とタグをチャットで入力")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            messages.send(clicker, "guild.create-prompt-name");
            chatInput.request(clicker, name -> {
                messages.send(clicker, "guild.create-prompt-tag");
                chatInput.request(clicker, tag -> clicker.performCommand("guild create " + name + " " + tag));
            });
        });
    }

    private GuiButton inviteButton(UUID guildId) {
        return new GuiButton(new ItemBuilder(Material.NAME_TAG).name("&%b招待")
                .lore(List.of("&%7クリックしてプレイヤー名をチャットで入力")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            messages.send(clicker, "guild.invite-prompt-player");
            chatInput.request(clicker, name -> {
                clicker.performCommand("guild invite " + name);
                guiManager.open(clicker, buildDetail(clicker, guildId, 0));
            });
        });
    }

    /**
     * Switches the clicker's currently-selected chat channel to guild (via
     * {@code rpg.extra.chat.command.ChatChannelCommand}/{@code ChatChannelService#switchChannel})
     * rather than prompting for a one-off message the way this button used to - every ordinary
     * chat line the player types afterward routes to guild chat until they switch again, so
     * there's nothing left to prompt for.
     */
    private GuiButton chatButton(UUID guildId) {
        return new GuiButton(new ItemBuilder(Material.WRITABLE_BOOK).name("&%bギルドチャットに切り替え")
                .lore(List.of("&%7クリックしてチャットの送信先をギルドに切り替える")).build(), (clicker, clickType) -> {
            clicker.performCommand("chat guild");
            guiManager.open(clicker, buildDetail(clicker, guildId, 0));
        });
    }

    private GuiButton leaveOrDisbandButton(UUID guildId, boolean isLeader) {
        String label = isLeader ? "&%c解散" : "&%c脱退";
        return new GuiButton(new ItemBuilder(Material.BARRIER).name(label).build(), (clicker, clickType) -> {
            clicker.performCommand(isLeader ? "guild disband" : "guild leave");
            guiManager.open(clicker, build(clicker, 0));
        });
    }

    private GuiButton guildButton(Guild guild) {
        List<String> lore = new ArrayList<>();
        lore.add("&%7メンバー: &%f" + guild.getMembers().size() + "人");
        lore.add("");
        lore.add("&%7クリックして詳細を表示");
        return new GuiButton(new ItemBuilder(Material.WHITE_BANNER)
                .name("&%e[" + guild.getTag() + "] " + guild.getName())
                .lore(lore)
                .build(), (clicker, clickType) -> guiManager.open(clicker, buildDetail(clicker, guild.getId(), 0)));
    }

    private GuiButton memberButton(Player viewer, UUID guildId, Map.Entry<UUID, GuildRole> member, GuildRole viewerRole) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(member.getKey());
        String name = offline.getName();
        boolean online = offline.isOnline();
        boolean canManage = name != null && !member.getKey().equals(viewer.getUniqueId())
                && (viewerRole == GuildRole.LEADER || viewerRole == GuildRole.OFFICER)
                && member.getValue() != GuildRole.LEADER;
        List<String> lore = new ArrayList<>();
        lore.add("&%7役職: &%f" + member.getValue().getDisplayName());
        if (canManage) {
            lore.add("&%eクリックして管理");
        }
        String displayName = (online ? "&%a" : "&%7") + (name != null ? name : member.getKey());
        return new GuiButton(GuiPlayerHead.build(offline, displayName, lore), (clicker, clickType) -> {
            if (canManage) {
                guiManager.open(clicker, buildMemberActions(clicker, guildId, member.getKey(), name, viewerRole, member.getValue()));
            }
        });
    }

    private Gui buildMemberActions(Player viewer, UUID guildId, UUID targetId, String targetName,
                                    GuildRole viewerRole, GuildRole targetRole) {
        Gui gui = new Gui("&%8メンバー操作 - " + targetName, 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, buildDetail(clicker, guildId, 0))));

        int slot = 11;
        if (viewerRole == GuildRole.LEADER) {
            if (targetRole != GuildRole.OFFICER) {
                gui.set(slot++, memberActionButton(guildId, "&%a昇格", "guild promote " + targetName));
            }
            if (targetRole != GuildRole.MEMBER) {
                gui.set(slot++, memberActionButton(guildId, "&%e降格", "guild demote " + targetName));
            }
            gui.set(slot++, memberActionButton(guildId, "&%6リーダー権限を譲渡", "guild transfer " + targetName));
        }
        gui.set(slot, memberActionButton(guildId, "&%c追放", "guild kick " + targetName));
        return gui;
    }

    private GuiButton memberActionButton(UUID guildId, String label, String command) {
        return new GuiButton(new ItemBuilder(Material.PAPER).name(label).build(), (clicker, clickType) -> {
            clicker.performCommand(command);
            guiManager.open(clicker, buildDetail(clicker, guildId, 0));
        });
    }
}
