package rpg.gui.framework;

import org.bukkit.Material;
import org.bukkit.Sound;
import rpg.util.ItemBuilder;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Lays a list out across pages of a {@link Gui}, adding prev/next-page nav buttons only where
 * another page actually exists. Shared implementation for what orelia-world's
 * {@code DungeonGuiScreen} and orelia-extra's {@code AchievementGuiScreen} each grew
 * independently (they can't depend on each other), and the paging tool any screen that
 * currently truncates or overflows a fixed-size {@link Gui} (shop/crafting/job-change/quest)
 * can move onto instead.
 */
public final class GuiPaginator {

    private GuiPaginator() {
    }

    /**
     * @param page         zero-based page index
     * @param toButton     builds the button for one item
     * @param pageBuilder  rebuilds the whole screen for a given page (e.g. {@code p -> build(player, p)})
     */
    public static <T> void placePage(GuiManager guiManager, Gui gui, GuiPageLayout layout,
                                      List<T> items, int page,
                                      Function<T, GuiButton> toButton,
                                      IntFunction<Gui> pageBuilder) {
        int pageSize = layout.pageSize();
        int start = pageStart(page, pageSize, items.size());
        int end = pageEnd(start, pageSize, items.size());
        List<T> pageItems = items.subList(start, end);

        int[] slots = layout.itemSlots();
        for (int i = 0; i < pageItems.size(); i++) {
            gui.set(slots[i], toButton.apply(pageItems.get(i)));
        }
        if (hasPreviousPage(page)) {
            gui.set(layout.prevSlot(), navButton("&%7« 前のページ",
                    (clicker, clickType) -> guiManager.open(clicker, pageBuilder.apply(page - 1))));
        }
        if (hasNextPage(end, items.size())) {
            gui.set(layout.nextSlot(), navButton("&%7次のページ »",
                    (clicker, clickType) -> guiManager.open(clicker, pageBuilder.apply(page + 1))));
        }
    }

    // Pure index arithmetic, deliberately free of any Bukkit type - GuiPaginatorTest exercises
    // these directly since Material/ItemStack need a running server to initialize.
    static int pageStart(int page, int pageSize, int totalSize) {
        return Math.min(page * pageSize, totalSize);
    }

    static int pageEnd(int start, int pageSize, int totalSize) {
        return Math.min(start + pageSize, totalSize);
    }

    static boolean hasPreviousPage(int page) {
        return page > 0;
    }

    static boolean hasNextPage(int end, int totalSize) {
        return end < totalSize;
    }

    private static GuiButton navButton(String label, GuiButton.ClickAction action) {
        return new GuiButton(new ItemBuilder(Material.ARROW).name(label).build(), action, Sound.UI_BUTTON_CLICK);
    }
}
