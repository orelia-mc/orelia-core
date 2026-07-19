package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;
import rpg.util.ItemBuilder;

/**
 * Read-only view of the player's final stats (SOW section 17 "ステータス"). Stats can change
 * while this screen is open (level-up, buff apply/expire, equipment swap elsewhere) - since
 * there's no single event that covers all of those, {@code GuiModule} periodically calls
 * {@link #refresh} for any player with this screen open (tag {@link #TAG}) instead.
 */
public final class StatusGuiScreen {

    public static final String TAG = "status";

    private static final int HEAD_SLOT = 4;
    private static final Material[] ICONS = {Material.REDSTONE, Material.LAPIS_LAZULI, Material.IRON_INGOT,
            Material.SHIELD, Material.EMERALD, Material.BLAZE_POWDER, Material.SUGAR};

    private final StatusService statusService;
    private final GuiConfig guiConfig;

    public StatusGuiScreen(StatusService statusService, GuiConfig guiConfig) {
        this.statusService = statusService;
        this.guiConfig = guiConfig;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("status", "&%8ステータス"), 27).tag(TAG);
        StatSheet stats = statusService.getFinalStats(player.getUniqueId()).orElse(StatSheet.empty());
        gui.set(HEAD_SLOT, GuiButton.display(headIcon(player)));
        StatType[] types = StatType.values();
        for (int i = 0; i < types.length; i++) {
            gui.set(10 + i, GuiButton.display(statIcon(stats, types[i], i)));
        }
        return gui;
    }

    /** Re-renders every stat icon into {@code inventory} without rebuilding the whole {@link Gui}. */
    public void refresh(Player player, org.bukkit.inventory.Inventory inventory) {
        StatSheet stats = statusService.getFinalStats(player.getUniqueId()).orElse(StatSheet.empty());
        inventory.setItem(HEAD_SLOT, headIcon(player));
        StatType[] types = StatType.values();
        for (int i = 0; i < types.length; i++) {
            inventory.setItem(10 + i, statIcon(stats, types[i], i));
        }
    }

    private ItemStack headIcon(Player player) {
        int level = statusService.component(player.getUniqueId()).map(PlayerStatusComponent::getLevel).orElse(1);
        return new ItemBuilder(Material.PLAYER_HEAD)
                .name("&%e" + player.getName())
                .lore("&%7Lv. " + level)
                .build();
    }

    private ItemStack statIcon(StatSheet stats, StatType type, int index) {
        return new ItemBuilder(ICONS[index % ICONS.length])
                .name("&%f" + type.name().replace('_', '-'))
                .lore("&%7" + formatStat(stats.get(type)))
                .build();
    }

    /** Whole numbers show without a trailing ".0"; anything else keeps one decimal place. */
    private String formatStat(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
