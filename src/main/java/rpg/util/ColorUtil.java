package rpg.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Translates {@code &}-coded strings (as used across every messages.yml / items.yml entry)
 * into legacy section-coded text or Adventure {@link Component}s.
 */
public final class ColorUtil {

    private static final char AMPERSAND = '&';
    private static final char SECTION = '§';
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private ColorUtil() {
    }

    public static String colorize(String input) {
        if (input == null) {
            return "";
        }
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == AMPERSAND && COLOR_CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = SECTION;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static Component component(String input) {
        return LegacyComponentSerializer.legacySection().deserialize(colorize(input));
    }
}
