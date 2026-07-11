package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;
import rpg.util.ItemBuilder;

/**
 * Read-only view of the player's final stats (SOW section 17 "ステータス").
 */
public final class StatusGuiScreen {

    private final StatusService statusService;
    private final GuiConfig guiConfig;

    public StatusGuiScreen(StatusService statusService, GuiConfig guiConfig) {
        this.statusService = statusService;
        this.guiConfig = guiConfig;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("status", "&8ステータス"), 27);
        StatSheet stats = statusService.getFinalStats(player.getUniqueId()).orElse(StatSheet.empty());
        int level = statusService.component(player.getUniqueId()).map(PlayerStatusComponent::getLevel).orElse(1);

        gui.set(4, GuiButton.display(new ItemBuilder(Material.PLAYER_HEAD)
                .name("&e" + player.getName())
                .lore("&7Lv. " + level)
                .build()));

        Material[] icons = {Material.REDSTONE, Material.LAPIS_LAZULI, Material.IRON_INGOT, Material.SHIELD,
                Material.FEATHER, Material.ARROW, Material.BOOK, Material.EMERALD, Material.BLAZE_POWDER,
                Material.SUGAR};
        StatType[] types = StatType.values();
        for (int i = 0; i < types.length; i++) {
            gui.set(10 + i, GuiButton.display(new ItemBuilder(icons[i % icons.length])
                    .name("&f" + types[i].name().replace('_', '-'))
                    .lore("&7" + stats.get(types[i]))
                    .build()));
        }
        return gui;
    }
}
