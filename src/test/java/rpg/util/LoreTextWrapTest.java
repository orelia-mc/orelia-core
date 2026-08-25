package rpg.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreTextWrapTest {

    @Test
    void shortTextStaysOnOneLine() {
        assertEquals(List.of("&%7短い文章"), LoreTextWrap.wrap("&%7短い文章"));
    }

    @Test
    void emptyTextReturnsOneEmptyLine() {
        assertEquals(List.of(""), LoreTextWrap.wrap(""));
    }

    @Test
    void longJapaneseTextWrapsAroundFifteenCharacters() {
        // 30 characters, all full-width (weight 2 each = 60 units) - wraps into 2 lines at the
        // default 30-unit target (15 full-width chars per line).
        String text = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほ";
        assertEquals(30, text.length());
        List<String> lines = LoreTextWrap.wrap(text);
        assertEquals(2, lines.size());
        assertEquals(15, lines.get(0).length());
        assertEquals(15, lines.get(1).length());
    }

    @Test
    void colorCodeIsNeverSplitAcrossALineBreak() {
        String text = "&%7あいうえおかきくけこさしすせそ&%aたちつてと";
        List<String> lines = LoreTextWrap.wrap(text);
        for (String line : lines) {
            // Every "&" in a wrapped line must be immediately followed by "%<char>" - never a
            // dangling "&" left at the very end of a line with its code split onto the next.
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '&') {
                    assertTrue(i + 2 < line.length(), "dangling & at end of line: " + line);
                    assertEquals('%', line.charAt(i + 1));
                }
            }
        }
    }

    @Test
    void activeColorCodeCarriesForwardToTheNextLine() {
        String text = "&%aいちにいさんしごろくしちはちきゅうじゅう" + "じゅういちじゅうに";
        List<String> lines = LoreTextWrap.wrap(text);
        assertTrue(lines.size() >= 2);
        for (String line : lines) {
            assertTrue(line.startsWith("&%a"), "expected line to start with the carried-forward color code: " + line);
        }
    }

    @Test
    void asciiCostsHalfAsMuchWidthAsFullWidthCharacters() {
        // 30 ASCII chars (weight 1 each = 30 units) fits exactly one line at the default
        // 30-unit target - twice as many characters as the equivalent all-Japanese case above.
        String thirtyAscii = "abcdefghijklmnopqrstuvwxyz1234";
        assertEquals(List.of(thirtyAscii), LoreTextWrap.wrap(thirtyAscii));

        // One more character tips it over into a second line.
        List<String> lines = LoreTextWrap.wrap(thirtyAscii + "5");
        assertEquals(2, lines.size());
        assertEquals(30, lines.get(0).length());
        assertEquals("5", lines.get(1));
    }
}
