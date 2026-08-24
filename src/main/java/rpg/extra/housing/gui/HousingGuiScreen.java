package rpg.extra.housing.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.housing.model.HousePlot;
import rpg.extra.housing.service.HousingService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.util.ItemBuilder;
import rpg.util.MoneyFormat;

import java.util.List;
import java.util.Map;

/**
 * Lists every unowned plot from {@code housing.yml}; clicking one buys it. If the viewer
 * already owns a plot, a "go home" button appears in slot 0 instead. Same shape as
 * {@code RankingGuiScreen} - a plain {@code build(Player) -> Gui} method reusing
 * orelia-core's generic Gui framework.
 */
public final class HousingGuiScreen {

    private final HousingService housingService;
    private final MessageManager messages;

    public HousingGuiScreen(HousingService housingService, MessageManager messages) {
        this.housingService = housingService;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8マイホーム", 27);

        int slot = 0;
        if (housingService.getOwnedPlot(player.getUniqueId()).isPresent()) {
            gui.set(slot++, new GuiButton(new ItemBuilder(Material.OAK_DOOR)
                    .name("&%eマイホームへ帰る")
                    .lore(List.of("&%7クリックして移動"))
                    .build(), (clicker, clickType) -> {
                clicker.closeInventory();
                HousingService.ActionResult result = housingService.teleportHome(clicker);
                if (result == HousingService.ActionResult.OK) {
                    messages.send(clicker, "housing.teleported");
                } else {
                    report(clicker, result);
                }
            }));
        }

        Map<String, HousePlot> available = housingService.getAvailablePlots();
        for (HousePlot plot : available.values()) {
            if (slot >= 27) {
                break;
            }
            gui.set(slot++, new GuiButton(new ItemBuilder(Material.OAK_DOOR)
                    .name("&%e" + plot.getName())
                    .lore(List.of("&%7価格: &%f" + MoneyFormat.format(plot.getPrice()), "&%7クリックして購入"))
                    .build(), (clicker, clickType) -> {
                HousingService.ActionResult result = housingService.purchase(clicker, plot.getId());
                if (result == HousingService.ActionResult.OK) {
                    messages.send(clicker, "housing.purchased", "id", plot.getId());
                } else {
                    report(clicker, result);
                }
            }));
        }
        return gui;
    }

    private void report(Player player, HousingService.ActionResult result) {
        String key = switch (result) {
            case OK -> "command.ok";
            case PLOT_NOT_FOUND -> "housing.plot-not-found";
            case PLOT_TAKEN -> "housing.plot-taken";
            case ALREADY_OWN_A_HOUSE -> "housing.already-own-house";
            case INSUFFICIENT_FUNDS -> "housing.insufficient-funds";
            case NO_HOUSE -> "housing.no-house";
            case WORLD_NOT_FOUND -> "housing.world-not-found";
        };
        messages.send(player, key);
    }
}
