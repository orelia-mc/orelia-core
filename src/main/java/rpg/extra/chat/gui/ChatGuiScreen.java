package rpg.extra.chat.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.extra.chat.model.ChatBadge;
import rpg.extra.chat.model.ChatChannel;
import rpg.extra.chat.service.ChatChannelService;
import rpg.extra.chat.service.ChatMuteService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.util.ItemBuilder;

import java.util.List;

/**
 * GUI counterpart of {@code /ol chat}'s channel-switch and mute-toggle subcommands (new entry
 * point - {@code /ol chat <channel>}/{@code /ol chat mute [category]} on the command line are
 * unchanged). Every action dispatches through {@link Player#performCommand} to the real
 * {@code /chat ...} command rather than calling {@link ChatChannelService}/{@link ChatMuteService}
 * directly, same convention as every other social GUI screen in this package - a channel switch
 * that's actually rejected (not in a party/guild, no admin permission) reports the exact same
 * error message the command line already does, with no separate logic needed here.
 */
public final class ChatGuiScreen {

    /** Centered 4-button row (row 2, slots 9-17) for the four selectable channels. */
    private static final int CHANNEL_PUBLIC_SLOT = 11;
    private static final int CHANNEL_PARTY_SLOT = 12;
    private static final int CHANNEL_GUILD_SLOT = 13;
    private static final int CHANNEL_ADMIN_SLOT = 14;
    /** Centered 3-button row (row 3, slots 18-26) for the three mutable categories - admin has no chat-line mute, see {@link ChatBadge}'s own doc comment. */
    private static final int MUTE_PUBLIC_SLOT = 21;
    private static final int MUTE_PARTY_SLOT = 22;
    private static final int MUTE_GUILD_SLOT = 23;
    /** Unlike most sibling screens' BACK_SLOT=22, this screen's own mute row already occupies 22 - picked a different free slot rather than relocating a button players already know. */
    private static final int PARENT_BACK_SLOT = 26;

    private final ChatChannelService channelService;
    private final ChatMuteService muteService;
    private final GuiManager guiManager;

    public ChatGuiScreen(ChatChannelService channelService, ChatMuteService muteService, GuiManager guiManager) {
        this.channelService = channelService;
        this.muteService = muteService;
        this.guiManager = guiManager;
    }

    public Gui build(Player player) {
        return build(player, null);
    }

    /** {@code backButton} - non-null when opened from a parent menu (e.g. the nether-star player-info item) that this screen should return to; placed in {@link #PARENT_BACK_SLOT}, otherwise left empty. */
    public Gui build(Player player, GuiButton backButton) {
        Gui gui = new Gui("&%8チャット設定", 27);
        if (backButton != null) {
            gui.set(PARENT_BACK_SLOT, backButton);
        }
        ChatChannel current = channelService.getChannel(player.getUniqueId());

        gui.set(CHANNEL_PUBLIC_SLOT, channelButton(ChatChannel.PUBLIC, Material.PAPER, current, backButton));
        gui.set(CHANNEL_PARTY_SLOT, channelButton(ChatChannel.PARTY, Material.MAGENTA_DYE, current, backButton));
        gui.set(CHANNEL_GUILD_SLOT, channelButton(ChatChannel.GUILD, Material.LIME_DYE, current, backButton));
        gui.set(CHANNEL_ADMIN_SLOT, channelButton(ChatChannel.ADMIN, Material.REDSTONE, current, backButton));

        if (muteService.isEnabled()) {
            var muted = muteService.getMuted(player.getUniqueId());
            gui.set(MUTE_PUBLIC_SLOT, muteButton(ChatBadge.PUBLIC, muted.contains(ChatBadge.PUBLIC), backButton));
            gui.set(MUTE_PARTY_SLOT, muteButton(ChatBadge.PARTY, muted.contains(ChatBadge.PARTY), backButton));
            gui.set(MUTE_GUILD_SLOT, muteButton(ChatBadge.GUILD, muted.contains(ChatBadge.GUILD), backButton));
        }
        return gui;
    }

    private GuiButton channelButton(ChatChannel channel, Material material, ChatChannel current, GuiButton backButton) {
        boolean selected = channel == current;
        List<String> lore = selected ? List.of("&%a現在選択中") : List.of("&%7クリックして切り替え");
        String name = (selected ? "&%a" : "&%e") + channel.getDisplayName();
        return new GuiButton(new ItemBuilder(material).name(name).lore(lore).build(), (clicker, clickType) -> {
            clicker.performCommand("chat " + channel.name().toLowerCase());
            guiManager.open(clicker, build(clicker, backButton));
        });
    }

    private GuiButton muteButton(ChatBadge category, boolean muted, GuiButton backButton) {
        Material material = muted ? Material.BARRIER : Material.NOTE_BLOCK;
        String name = (muted ? "&%c" : "&%a") + category.getDisplayName() + " " + (muted ? "ミュート中" : "ミュート解除中");
        List<String> lore = List.of("&%7クリックして切り替え");
        return new GuiButton(new ItemBuilder(material).name(name).lore(lore).build(), (clicker, clickType) -> {
            clicker.performCommand("chat mute " + category.name().toLowerCase());
            guiManager.open(clicker, build(clicker, backButton));
        });
    }
}
