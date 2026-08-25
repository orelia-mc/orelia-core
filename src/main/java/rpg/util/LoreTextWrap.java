package rpg.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a long line of {@code &}-coded text into several shorter lines for GUI item lore -
 * Minecraft's tooltip renderer never wraps lore on its own, so a message longer than roughly
 * 14-16 full-width (Japanese) characters just runs off the edge of the screen instead (SOW
 * follow-up: mail body text). Approximates rendered width rather than counting raw characters:
 * a full-width glyph (kana/kanji/fullwidth punctuation) counts as 2 units, everything else
 * (ASCII, halfwidth katakana) as 1 - so mixed Japanese/ASCII text wraps at roughly the same
 * *visual* width instead of favoring whichever script happens to dominate a given message,
 * without needing a full per-glyph Minecraft font metrics table.
 *
 * <p>{@link ColorUtil}'s {@code &%<char>} color codes are zero-width and are never split across
 * a line break; whichever code was last active when a break happens is carried forward to the
 * start of the next line, since each lore array entry is its own tooltip line with no formatting
 * carried over automatically from the previous one.
 */
public final class LoreTextWrap {

    /** ~15 full-width characters, the middle of the "14-16文字目くらい" the report asked for. */
    private static final int DEFAULT_TARGET_WIDTH = 30;

    private LoreTextWrap() {
    }

    public static List<String> wrap(String text) {
        return wrap(text, DEFAULT_TARGET_WIDTH);
    }

    public static List<String> wrap(String text, int targetWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        int width = 0;
        String activeCode = "";
        int i = 0;
        int length = text.length();
        while (i < length) {
            if (text.charAt(i) == '&' && i + 2 < length && text.charAt(i + 1) == '%') {
                String code = text.substring(i, i + 3);
                current.append(code);
                activeCode = code;
                i += 3;
                continue;
            }
            int codePoint = text.codePointAt(i);
            int charWidth = isWide(codePoint) ? 2 : 1;
            if (width + charWidth > targetWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(activeCode);
                width = 0;
            }
            current.appendCodePoint(codePoint);
            width += charWidth;
            i += Character.charCount(codePoint);
        }
        lines.add(current.toString());
        return lines;
    }

    /** Common CJK/fullwidth Unicode ranges - covers Japanese (the only language this codebase's user-facing text uses) plus Korean/fullwidth-form punctuation for good measure. */
    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x3000 && codePoint <= 0x303F)    // CJK symbols/punctuation
                || (codePoint >= 0x3040 && codePoint <= 0x30FF) // Hiragana/Katakana
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF) // CJK Unified Ideographs Extension A
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF) // CJK Unified Ideographs
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3) // Hangul syllables
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF) // CJK compatibility ideographs
                || (codePoint >= 0xFF01 && codePoint <= 0xFF60) // Fullwidth forms (excludes halfwidth katakana FF61-FF9F, which renders narrow)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6); // Fullwidth signs
    }
}
