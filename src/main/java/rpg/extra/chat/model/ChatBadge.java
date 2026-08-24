package rpg.extra.chat.model;

import net.kyori.adventure.text.Component;
import rpg.util.ColorUtil;

/**
 * Category a chat line belongs to - both the icon badge prepended to the line (an alternative
 * to a vanilla "[Party]"-style text prefix) and the taxonomy
 * {@link rpg.extra.chat.service.ChatMuteService} mutes by. Icons are picked from symbols that
 * render in vanilla Minecraft's default chat font without needing orelia-resourcepack.
 *
 * <p>{@link #COMBAT} has no call site wired up yet - boss ability/phase announcements already
 * moved to ActionBar/Title (see dynamic-chat-design.md), so this is a landing spot for future
 * combat-related chat lines (kill logs, etc.) rather than something currently sent.
 */
public enum ChatBadge {

    COMBAT("⚔", "&%c", "戦闘"),
    SYSTEM("✉", "&%6", "システム"),
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
