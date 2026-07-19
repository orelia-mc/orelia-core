package rpg.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorUtilTest {

    @Test
    void colorizeTranslatesLegacyCodes() {
        assertEquals("§aHello", ColorUtil.colorize("&aHello"));
    }

    @Test
    void colorizeLeavesPlainTextUntouched() {
        assertEquals("no codes here", ColorUtil.colorize("no codes here"));
    }

    @Test
    void colorizeExpandsHexIntoLegacySectionSequence() {
        assertEquals("§x§f§f§0§0§0§0test", ColorUtil.colorize("&#FF0000test"));
    }

    @Test
    void colorizeHandlesMixOfLegacyAndHexCodes() {
        assertEquals("§a§x§0§0§f§f§0§0b", ColorUtil.colorize("&a&#00FF00b"));
    }

    @Test
    void colorizeIsIdempotentOnAlreadyColorizedInput() {
        String once = ColorUtil.colorize("&a&#00FF00b");
        assertEquals(once, ColorUtil.colorize(once));
    }

    @Test
    void colorizeOfNullReturnsEmptyString() {
        assertEquals("", ColorUtil.colorize(null));
    }

    @Test
    void colorizeExpandsMappedCustomCode() {
        assertEquals("§x§d§6§6§f§6§ftest", ColorUtil.colorize("&%ctest"));
    }

    @Test
    void colorizeLeavesUnmappedCustomCodeUntouched() {
        assertEquals("&%ztest", ColorUtil.colorize("&%ztest"));
    }
}
