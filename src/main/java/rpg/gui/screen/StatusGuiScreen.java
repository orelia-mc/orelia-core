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

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view of the player's final stats (SOW section 17 "ステータス"). Stats can change
 * while this screen is open (level-up, buff apply/expire, equipment swap elsewhere) - since
 * there's no single event that covers all of those, {@code GuiModule} periodically calls
 * {@link #refresh} for any player with this screen open (tag {@link #TAG}) instead.
 *
 * <p>Every {@link StatType} is listed in one item's lore (rather than one icon per stat) so
 * the player can read them all at once without hovering over each icon individually - this
 * matters more now that relics can grant up to 7 extra stat types (elemental damage x6, SP
 * recovery) on top of the base 7.
 */
public final class StatusGuiScreen {

    public static final String TAG = "status";

    private static final int HEAD_SLOT = 4;
    private static final int STATS_SLOT = 13;

    private final StatusService statusService;
    private final GuiConfig guiConfig;

    public StatusGuiScreen(StatusService statusService, GuiConfig guiConfig) {
        this.statusService = statusService;
        this.guiConfig = guiConfig;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("status", "&%8ステータス"), 27);
        gui.set(HEAD_SLOT, GuiButton.display(headIcon(player)));
        gui.set(STATS_SLOT, GuiButton.display(statsIcon(player)));
        return gui;
    }

    /** Re-renders both icons into {@code inventory} without rebuilding the whole {@link Gui}. */
    public void refresh(Player player, org.bukkit.inventory.Inventory inventory) {
        inventory.setItem(HEAD_SLOT, headIcon(player));
        inventory.setItem(STATS_SLOT, statsIcon(player));
    }

    private ItemStack headIcon(Player player) {
        int level = statusService.component(player.getUniqueId()).map(PlayerStatusComponent::getLevel).orElse(1);
        return new ItemBuilder(Material.PLAYER_HEAD)
                .name("&%e" + player.getName())
                .lore("&%7Lv. " + level)
                .build();
    }

    private ItemStack statsIcon(Player player) {
        StatSheet stats = statusService.getFinalStats(player.getUniqueId()).orElse(StatSheet.empty());
        List<String> lore = new ArrayList<>();
        for (StatType type : StatType.values()) {
            lore.add("&%7" + type.name().replace('_', '-') + ": &%f" + formatStat(stats.get(type)));
        }
        return new ItemBuilder(Material.NETHER_STAR)
                .name("&%eステータス")
                .lore(lore)
                .build();
    }

    /** Whole numbers show without a trailing ".0"; anything else keeps one decimal place. */
    private String formatStat(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
