package rpg.gui.framework;

import org.bukkit.Material;
import org.bukkit.Sound;
import rpg.util.ItemBuilder;

import java.util.List;

/**
 * A generic "are you sure?" screen for irreversible/money-moving actions (purchases, job
 * changes, relic upgrades, ...) - interposed as its own screen rather than a same-screen
 * double-click, per the SOW decision to keep destructive actions behind a confirmation step.
 * Holds no state of its own: {@code onConfirm}/{@code onCancel} are expected to be closures
 * that already captured whatever they need to act on, same one-shot-build pattern as every
 * other screen in {@code rpg.gui.screen}.
 */
public final class ConfirmGuiScreen {

    private static final int CONFIRM_SLOT = 11;
    private static final int INFO_SLOT = 13;
    private static final int CANCEL_SLOT = 15;

    private ConfirmGuiScreen() {
    }

    /**
     * @param title       screen title (caller resolves any {@code messages.yml} text beforehand)
     * @param description lore lines shown on the info icon (same - caller resolves placeholders first)
     */
    public static Gui build(String title, List<String> description, Runnable onConfirm, Runnable onCancel) {
        Gui gui = new Gui(title, 27);
        gui.set(INFO_SLOT, GuiButton.display(new ItemBuilder(Material.PAPER)
                .name("&%e確認")
                .lore(description)
                .build()));
        gui.set(CONFIRM_SLOT, new GuiButton(new ItemBuilder(Material.LIME_WOOL)
                .name("&%aはい")
                .build(), (player, clickType) -> {
            player.closeInventory();
            onConfirm.run();
        }));
        gui.set(CANCEL_SLOT, new GuiButton(new ItemBuilder(Material.RED_WOOL)
                .name("&%cいいえ")
                .build(), (player, clickType) -> {
            player.closeInventory();
            onCancel.run();
        }, Sound.UI_BUTTON_CLICK));
        return gui;
    }
}
