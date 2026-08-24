package rpg.extra.chat.model;

import net.kyori.adventure.text.Component;
import rpg.util.ColorUtil;

/**
 * Category a chat line belongs to - both the icon badge prepended to the line (an alternative
 * to a vanilla "[Party]"-style text prefix) and the taxonomy
 * {@link rpg.extra.chat.service.ChatMuteService} mutes by. Icons are picked from symbols that
 * render in vanilla Minecraft's default chat font without needing orelia-resourcepack.
 *
 * <p>Deliberately covers only the three channels a player actually types messages into
 * (public/party/guild) - {@code /chat mute} mutes a channel's <em>user-sent</em> chat lines
 * only. System-driven notifications (mail unread, party/guild invite received, trade request
 * received, ...) are never gated by this and always show/play regardless of mute state - see
 * each of those call sites for why. An earlier revision also had {@code COMBAT} (never wired to
 * an actual chat line - boss ability/phase announcements moved to ActionBar/Title instead) and
 * {@code SYSTEM} (used only to gate the mail-unread notice, which shouldn't be mutable per the
 * rule above) as mutable categories; both were removed rather than kept unused.
 */
public enum ChatBadge {

    PUBLIC("✎", "&%f", "全体"),
    PARTY("❤", "&%9", "パーティー"),
    GUILD("⚑", "&%a", "ギルド");

    private final String icon;
    private final String colorCode;
    private final String displayName;

    ChatBadge(String icon, String colorCode, String displayName) {
        this.icon = icon;
        this.colorCode = colorCode;
        this.displayName = displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Prepends this badge's colored icon to {@code line} (e.g. "&%9❤ " + line). */
    public Component decorate(Component line) {
        return ColorUtil.component(colorCode + icon + "&r ").append(line);
    }
}
