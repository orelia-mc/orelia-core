package rpg.gui.framework;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * One clickable (or purely decorative) slot in a {@link Gui}.
 */
public final class GuiButton {

    /** Default click sound used by the 2-arg constructor - most buttons don't need to think about this. */
    public static final Sound DEFAULT_CLICK_SOUND = Sound.BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON;

    /** Invoked on click; receives the player and the click type name (e.g. "LEFT", "RIGHT"). */
    public interface ClickAction {
        void onClick(Player player, String clickType);
    }

    private final ItemStack icon;
    private final ClickAction action;
    private final Sound sound;

    public GuiButton(ItemStack icon, ClickAction action) {
        this(icon, action, DEFAULT_CLICK_SOUND);
    }

    /** {@code sound} may be {@code null} for a silent button (see {@link #display}). */
    public GuiButton(ItemStack icon, ClickAction action, Sound sound) {
        this.icon = icon;
        this.action = action;
        this.sound = sound;
    }

    public static GuiButton display(ItemStack icon) {
        return new GuiButton(icon, (player, clickType) -> {
        }, null);
    }

    public ItemStack getIcon() {
        return icon;
    }

    public ClickAction getAction() {
        return action;
    }

    public Sound getSound() {
        return sound;
    }
}
