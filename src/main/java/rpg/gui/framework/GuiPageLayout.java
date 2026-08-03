package rpg.gui.framework;

/**
 * Slot arrangement for a paginated {@link Gui} screen: the slots items are placed into, and
 * where the prev/next-page nav buttons go. Screen-specific (e.g. a 27-slot screen's interior
 * row) - {@link GuiPaginator} itself makes no assumption about layout.
 */
public record GuiPageLayout(int[] itemSlots, int prevSlot, int nextSlot) {

    public int pageSize() {
        return itemSlots.length;
    }
}
