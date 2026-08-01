package rpg.gui.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link GuiPaginator}'s pure index arithmetic directly (not {@code placePage}
 * itself) - {@code placePage} builds nav-button {@link org.bukkit.inventory.ItemStack}s via
 * {@link org.bukkit.Material}, which requires a running server's {@code ItemFactory} to
 * initialize and isn't available in this project's unit test setup (no test server/MockBukkit
 * dependency).
 */
class GuiPaginatorTest {

    private static final int PAGE_SIZE = 7;

    @Test
    void exactlyOnePageHasNoNavButtons() {
        int totalSize = 7;
        int start = GuiPaginator.pageStart(0, PAGE_SIZE, totalSize);
        int end = GuiPaginator.pageEnd(start, PAGE_SIZE, totalSize);

        assertEquals(0, start);
        assertEquals(7, end);
        assertFalse(GuiPaginator.hasPreviousPage(0));
        assertFalse(GuiPaginator.hasNextPage(end, totalSize));
    }

    @Test
    void oneItemOverflowsToSecondPage() {
        int totalSize = 8;

        int firstStart = GuiPaginator.pageStart(0, PAGE_SIZE, totalSize);
        int firstEnd = GuiPaginator.pageEnd(firstStart, PAGE_SIZE, totalSize);
        assertFalse(GuiPaginator.hasPreviousPage(0));
        assertTrue(GuiPaginator.hasNextPage(firstEnd, totalSize));

        int secondStart = GuiPaginator.pageStart(1, PAGE_SIZE, totalSize);
        int secondEnd = GuiPaginator.pageEnd(secondStart, PAGE_SIZE, totalSize);
        assertEquals(7, secondStart);
        assertEquals(8, secondEnd);
        assertTrue(GuiPaginator.hasPreviousPage(1));
        assertFalse(GuiPaginator.hasNextPage(secondEnd, totalSize));
    }

    @Test
    void emptyListHasNoPagesAndNoNavButtons() {
        int totalSize = 0;
        int start = GuiPaginator.pageStart(0, PAGE_SIZE, totalSize);
        int end = GuiPaginator.pageEnd(start, PAGE_SIZE, totalSize);

        assertEquals(0, start);
        assertEquals(0, end);
        assertFalse(GuiPaginator.hasPreviousPage(0));
        assertFalse(GuiPaginator.hasNextPage(end, totalSize));
    }

    @Test
    void lastPageWithPartialItemsHasNoNextButton() {
        // 15 items, page size 7 -> pages [0,7) [7,14) [14,15): last page has 1 item, no next.
        int totalSize = 15;
        int start = GuiPaginator.pageStart(2, PAGE_SIZE, totalSize);
        int end = GuiPaginator.pageEnd(start, PAGE_SIZE, totalSize);

        assertEquals(14, start);
        assertEquals(15, end);
        assertTrue(GuiPaginator.hasPreviousPage(2));
        assertFalse(GuiPaginator.hasNextPage(end, totalSize));
    }

    @Test
    void pageStartClampsToTotalSizeForOutOfRangePage() {
        // Requesting a page far past the end shouldn't produce a negative-length range.
        int totalSize = 5;
        int start = GuiPaginator.pageStart(10, PAGE_SIZE, totalSize);
        int end = GuiPaginator.pageEnd(start, PAGE_SIZE, totalSize);

        assertEquals(5, start);
        assertEquals(5, end);
    }
}
