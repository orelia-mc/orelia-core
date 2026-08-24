package rpg.extra.guild.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRole;
import rpg.extra.guild.service.GuildService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.gui.framework.GuiPageLayout;
import rpg.gui.framework.GuiPaginator;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI counterpart of {@code /ol guild list}/{@code /ol guild info}'s chat listings - a list of
 * every registered guild, click-through to a member roster. Same two-level drill-down shape as
 * orelia-world's {@code DungeonGuiScreen} (list -> detail), but this is the first screen in
 * either repo to actually adopt orelia-core's shared {@link GuiPaginator}/{@link GuiPageLayout}
 * instead of growing its own copy of the paging logic (see those classes' own doc comments).
 */
public final class GuildGuiScreen {

    private static final GuiPageLayout LIST_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final GuiPageLayout MEMBER_LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final int BACK_SLOT = 22;

    private final GuildService guildService;
    private final GuiManager guiManager;

    public GuildGuiScreen(GuildService guildService, GuiManager guiManager) {
        this.guildService = guildService;
        this.guiManager = guiManager;
    }

    public Gui build(Player player) {
        return build(player, 0);
    }

    private Gui build(Player player, int page) {
        Gui gui = new Gui("&%8ギルド一覧", 27);
        List<Guild> guilds = List.copyOf(guildService.getAllGuilds());
        GuiPaginator.placePage(guiManager, gui, LIST_LAYOUT, guilds, page,
                this::guildButton, p -> build(player, p));
        return gui;
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

        List<Map.Entry<UUID, GuildRole>> members = List.copyOf(guild.getMembers().entrySet());
        GuiPaginator.placePage(guiManager, gui, MEMBER_LAYOUT, members, page,
                this::memberButton, p -> buildDetail(player, guildId, p));
        return gui;
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

    private GuiButton memberButton(Map.Entry<UUID, GuildRole> member) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(member.getKey());
        String name = offline.getName();
        boolean online = offline.isOnline();
        List<String> lore = List.of("&%7役職: &%f" + member.getValue().getDisplayName());
        String displayName = (online ? "&%a" : "&%7") + (name != null ? name : member.getKey());
        if (!online) {
            return new GuiButton(new ItemBuilder(Material.SKELETON_SKULL).name(displayName).lore(lore).build(),
                    (clicker, clickType) -> {
                    });
        }
        // Built directly rather than through ItemBuilder - setOwningPlayer is SkullMeta-specific
        // and ItemBuilder's generic ItemMeta wrapping has no hook for it (see StatusGuiScreen's
        // own head icon for the same pattern). Without this, a plain PLAYER_HEAD ItemStack always
        // renders the default Steve skin instead of the member's own.
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(offline);
        meta.displayName(ColorUtil.component(displayName));
        meta.lore(lore.stream().map(ColorUtil::component).toList());
        head.setItemMeta(meta);
        return new GuiButton(head, (clicker, clickType) -> {
        });
    }
}
