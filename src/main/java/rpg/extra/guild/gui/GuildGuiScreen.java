package rpg.extra.guild.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import rpg.core.chat.ChatInputService;
import rpg.core.message.MessageManager;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRoleDefinition;
import rpg.extra.guild.service.GuildService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.gui.framework.GuiPageLayout;
import rpg.gui.framework.GuiPaginator;
import rpg.gui.framework.GuiPlayerHead;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * GUI counterpart of every {@code /guild} subcommand - list -> detail drill-down (same shape as
 * orelia-world's {@code DungeonGuiScreen}), plus every action the command line offers: create,
 * rename/retag, invite, accept/decline pending invites, leave, kick, assign/add/rename/remove a
 * custom role, transfer leadership, disband, and switch to guild chat. Free-text fields (guild
 * name/tag, an invitee's name, a role name) are collected via {@link ChatInputService} - the
 * player types a line in chat after being prompted, rather than a suggest-command prefill they'd
 * still have to press enter on - and every action is dispatched through
 * {@link Player#performCommand} to the real {@code /guild ...} command rather than calling
 * {@link GuildService} directly, so messaging, sound notifications, and broadcasts fire exactly
 * as they already do from chat.
 */
public final class GuildGuiScreen {

    private static final GuiPageLayout LIST_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final GuiPageLayout MEMBER_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final GuiPageLayout PENDING_LAYOUT = new GuiPageLayout(IntStream.range(0, 18).toArray(), 18, 26);
    /** Same 7-wide row as {@link #MEMBER_LAYOUT} - happens to fit {@link GuildService#MAX_ROLES_PER_GUILD} exactly on one page. */
    private static final GuiPageLayout ROLE_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final int BACK_SLOT = 22;
    private static final int CREATE_SLOT = 4;
    private static final int PENDING_INVITES_SLOT = 4;
    private static final int INVITE_SLOT = 19;
    private static final int CHAT_SLOT = 20;
    private static final int RENAME_SLOT = 18;
    private static final int RETAG_SLOT = 21;
    private static final int ROLE_MANAGE_SLOT = 23;
    private static final int LEAVE_SLOT = 24;
    private static final int ADD_ROLE_SLOT = 26;
    /** Centered 3-button row for member actions (row 2, slots 9-17, center 13) - shifted one slot right from the old 11/12/13 placement so it's actually centered. */
    private static final int MEMBER_ACTION_ROLE_SLOT = 12;
    private static final int MEMBER_ACTION_TRANSFER_SLOT = 13;
    private static final int MEMBER_ACTION_KICK_SLOT = 14;
    /** Symmetric pair flanking the same row's center for a role's own rename/delete actions. */
    private static final int ROLE_ACTION_RENAME_SLOT = 11;
    private static final int ROLE_ACTION_DELETE_SLOT = 15;

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
        int pendingCount = guildService.peekAllPendingInvites(player.getUniqueId()).size();
        if (pendingCount > 0) {
            gui.set(PENDING_INVITES_SLOT, new GuiButton(new ItemBuilder(Material.WRITTEN_BOOK)
                    .name("&%e招待が" + pendingCount + "件届いています")
                    .lore(List.of("&%7クリックして一覧を開く")).build(),
                    (clicker, clickType) -> guiManager.open(clicker, buildPendingInvites(clicker, 0))));
        } else if (guildService.getGuild(player.getUniqueId()).isEmpty()) {
            gui.set(CREATE_SLOT, createButton());
        }
        List<Guild> guilds = List.copyOf(guildService.getAllGuilds());
        GuiPaginator.placePage(guiManager, gui, LIST_LAYOUT, guilds, page,
                this::guildButton, p -> build(player, p));
        return gui;
    }

    /** Every guild currently inviting the viewer, oldest first - each entry answered independently, not just the single oldest one. */
    private Gui buildPendingInvites(Player player, int page) {
        Gui gui = new Gui("&%8届いているギルド招待", 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, build(clicker, 0))));

        List<Guild> invites = guildService.peekAllPendingInvites(player.getUniqueId());
        GuiPaginator.placePage(guiManager, gui, PENDING_LAYOUT, invites, page,
                this::pendingInviteButton, p -> buildPendingInvites(player, p));
        return gui;
    }

    private GuiButton pendingInviteButton(Guild guild) {
        List<String> lore = List.of("&%a左クリックで承認", "&%c右クリックで拒否");
        return new GuiButton(new ItemBuilder(Material.WHITE_BANNER).name("&%e[" + guild.getTag() + "] " + guild.getName())
                .lore(lore).build(), (clicker, clickType) -> {
            boolean decline = clickType != null && clickType.contains("RIGHT");
            clicker.performCommand("guild " + (decline ? "decline " : "accept ") + guild.getName());
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

        String viewerRoleId = guild.roleOf(player.getUniqueId());
        boolean isLeader = guild.getLeaderId().equals(player.getUniqueId());
        if (viewerRoleId != null) {
            if (isLeader) {
                gui.set(INVITE_SLOT, inviteButton(guildId));
                gui.set(RENAME_SLOT, renameButton(guildId));
                gui.set(RETAG_SLOT, retagButton(guildId));
                gui.set(ROLE_MANAGE_SLOT, roleManageButton(guildId));
            }
            gui.set(CHAT_SLOT, chatButton(guildId));
            gui.set(LEAVE_SLOT, leaveOrDisbandButton(guildId, isLeader));
        }

        List<Map.Entry<UUID, String>> members = List.copyOf(guild.getMembers().entrySet());
        GuiPaginator.placePage(guiManager, gui, MEMBER_LAYOUT, members, page,
                member -> memberButton(player, guild, member, isLeader), p -> buildDetail(player, guildId, p));
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

    private GuiButton renameButton(UUID guildId) {
        return new GuiButton(new ItemBuilder(Material.OAK_SIGN).name("&%b名前を変更")
                .lore(List.of("&%7クリックして新しい名前をチャットで入力")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            messages.send(clicker, "guild.rename-prompt");
            chatInput.request(clicker, name -> {
                clicker.performCommand("guild rename " + name);
                guiManager.open(clicker, buildDetail(clicker, guildId, 0));
            });
        });
    }

    private GuiButton retagButton(UUID guildId) {
        return new GuiButton(new ItemBuilder(Material.NAME_TAG).name("&%bタグを変更")
                .lore(List.of("&%7クリックして新しいタグをチャットで入力")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            messages.send(clicker, "guild.retag-prompt");
            chatInput.request(clicker, tag -> {
                clicker.performCommand("guild retag " + tag);
                guiManager.open(clicker, buildDetail(clicker, guildId, 0));
            });
        });
    }

    private GuiButton roleManageButton(UUID guildId) {
        return new GuiButton(new ItemBuilder(Material.BOOK).name("&%bロール管理")
                .lore(List.of("&%7クリックしてロールの追加・改名・削除")).build(),
                (clicker, clickType) -> guiManager.open(clicker, buildRoleManagement(clicker, guildId, 0)));
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

    private GuiButton memberButton(Player viewer, Guild guild, Map.Entry<UUID, String> member, boolean viewerIsLeader) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(member.getKey());
        String name = offline.getName();
        boolean online = offline.isOnline();
        boolean canManage = viewerIsLeader && name != null && !member.getKey().equals(viewer.getUniqueId())
                && !member.getKey().equals(guild.getLeaderId());
        List<String> lore = new ArrayList<>();
        lore.add("&%7役職: &%f" + guild.roleDisplayName(member.getValue()));
        if (canManage) {
            lore.add("&%eクリックして管理");
        }
        String displayName = (online ? "&%a" : "&%7") + (name != null ? name : member.getKey());
        return new GuiButton(GuiPlayerHead.build(offline, displayName, lore), (clicker, clickType) -> {
            if (canManage) {
                guiManager.open(clicker, buildMemberActions(clicker, guild.getId(), member.getKey(), name));
            }
        });
    }

    /** Always exactly 3 buttons (role/transfer/kick) - only reachable when the viewer is the leader (see {@link #memberButton}), so no more conditional promote/demote hiding like the old fixed-ladder version needed. */
    private Gui buildMemberActions(Player viewer, UUID guildId, UUID targetId, String targetName) {
        Gui gui = new Gui("&%8メンバー操作 - " + targetName, 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, buildDetail(clicker, guildId, 0))));

        gui.set(MEMBER_ACTION_ROLE_SLOT, new GuiButton(new ItemBuilder(Material.PAPER).name("&%bロールを選択")
                .lore(List.of("&%7クリックして役職を選ぶ")).build(),
                (clicker, clickType) -> guiManager.open(clicker, buildRolePicker(clicker, guildId, targetId, targetName))));
        gui.set(MEMBER_ACTION_TRANSFER_SLOT, memberActionButton(guildId, "&%6リーダー権限を譲渡", "guild transfer " + targetName));
        gui.set(MEMBER_ACTION_KICK_SLOT, memberActionButton(guildId, "&%c追放", "guild kick " + targetName));
        return gui;
    }

    /** One button per the guild's own custom role - clicking assigns it to {@code targetName} via {@code /guild role}. */
    private Gui buildRolePicker(Player viewer, UUID guildId, UUID targetId, String targetName) {
        Guild guild = guildService.getGuildById(guildId).orElse(null);
        Gui gui = new Gui("&%8ロールを選択 - " + targetName, 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, buildMemberActions(clicker, guildId, targetId, targetName))));
        if (guild == null) {
            return gui;
        }
        String currentRoleId = guild.roleOf(targetId);
        List<GuildRoleDefinition> roles = guild.getRoles();
        GuiPaginator.placePage(guiManager, gui, ROLE_LAYOUT, roles,
                0, role -> rolePickButton(guildId, targetId, targetName, role, role.id().equals(currentRoleId)),
                p -> buildRolePicker(viewer, guildId, targetId, targetName));
        return gui;
    }

    private GuiButton rolePickButton(UUID guildId, UUID targetId, String targetName, GuildRoleDefinition role, boolean current) {
        List<String> lore = current ? List.of("&%a現在のロール") : List.of("&%7クリックして割り当て");
        Material material = current ? Material.LIME_DYE : Material.GRAY_DYE;
        return new GuiButton(new ItemBuilder(material).name("&%e" + role.name()).lore(lore).build(), (clicker, clickType) -> {
            clicker.performCommand("guild role " + targetName + " " + role.name());
            guiManager.open(clicker, buildRolePicker(clicker, guildId, targetId, targetName));
        });
    }

    /** Every custom role this guild currently has, plus an "add" button when under {@link GuildService#MAX_ROLES_PER_GUILD}. */
    private Gui buildRoleManagement(Player player, UUID guildId, int page) {
        Guild guild = guildService.getGuildById(guildId).orElse(null);
        Gui gui = new Gui("&%8ロール管理", 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, buildDetail(clicker, guildId, 0))));
        if (guild == null) {
            return gui;
        }

        List<GuildRoleDefinition> roles = guild.getRoles();
        GuiPaginator.placePage(guiManager, gui, ROLE_LAYOUT, roles, page,
                role -> roleManagementButton(guildId, role), p -> buildRoleManagement(player, guildId, p));

        if (roles.size() < GuildService.MAX_ROLES_PER_GUILD) {
            gui.set(ADD_ROLE_SLOT, new GuiButton(new ItemBuilder(Material.EMERALD).name("&%aロールを追加")
                    .lore(List.of("&%7クリックして名前をチャットで入力")).build(), (clicker, clickType) -> {
                clicker.closeInventory();
                messages.send(clicker, "guild.addrole-prompt");
                chatInput.request(clicker, name -> {
                    clicker.performCommand("guild addrole " + name);
                    guiManager.open(clicker, buildRoleManagement(clicker, guildId, 0));
                });
            }));
        } else {
            gui.set(ADD_ROLE_SLOT, new GuiButton(new ItemBuilder(Material.BARRIER).name("&%cこれ以上ロールを追加できません")
                    .lore(List.of("&%7上限は" + GuildService.MAX_ROLES_PER_GUILD + "個です")).build(), (clicker, clickType) -> { }));
        }
        return gui;
    }

    private GuiButton roleManagementButton(UUID guildId, GuildRoleDefinition role) {
        return new GuiButton(new ItemBuilder(Material.PAPER).name("&%e" + role.name())
                .lore(List.of("&%7クリックして改名・削除")).build(),
                (clicker, clickType) -> guiManager.open(clicker, buildRoleActions(clicker, guildId, role.name())));
    }

    private Gui buildRoleActions(Player player, UUID guildId, String roleName) {
        Gui gui = new Gui("&%8ロール操作 - " + roleName, 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« 戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, buildRoleManagement(clicker, guildId, 0))));

        gui.set(ROLE_ACTION_RENAME_SLOT, new GuiButton(new ItemBuilder(Material.PAPER).name("&%b改名")
                .lore(List.of("&%7クリックして新しい名前をチャットで入力")).build(), (clicker, clickType) -> {
            clicker.closeInventory();
            messages.send(clicker, "guild.renamerole-prompt");
            chatInput.request(clicker, newName -> {
                clicker.performCommand("guild renamerole " + roleName + " " + newName);
                guiManager.open(clicker, buildRoleManagement(clicker, guildId, 0));
            });
        }));
        gui.set(ROLE_ACTION_DELETE_SLOT, new GuiButton(new ItemBuilder(Material.BARRIER).name("&%c削除")
                .lore(List.of("&%7クリックして削除", "&%7割り当て済みのメンバーがいると削除できません")).build(), (clicker, clickType) -> {
            clicker.performCommand("guild removerole " + roleName);
            guiManager.open(clicker, buildRoleManagement(clicker, guildId, 0));
        }));
        return gui;
    }

    private GuiButton memberActionButton(UUID guildId, String label, String command) {
        return new GuiButton(new ItemBuilder(Material.PAPER).name(label).build(), (clicker, clickType) -> {
            clicker.performCommand(command);
            guiManager.open(clicker, buildDetail(clicker, guildId, 0));
        });
    }
}
