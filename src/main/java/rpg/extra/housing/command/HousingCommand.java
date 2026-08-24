package rpg.extra.housing.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.housing.gui.HousingGuiScreen;
import rpg.extra.housing.model.HousePlot;
import rpg.extra.housing.service.HousingService;
import rpg.gui.framework.GuiManager;
import rpg.util.MoneyFormat;

import java.util.Map;

/**
 * {@code /ol house [list|gui|buy <plotId>|home]} (SOW HousingModule).
 */
public final class HousingCommand implements CommandExecutor {

    private final HousingService housingService;
    private final HousingGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public HousingCommand(HousingService housingService, HousingGuiScreen guiScreen, GuiManager guiManager, MessageManager messages) {
        this.housingService = housingService;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage.house");
            return true;
        }
        if (args[0].equalsIgnoreCase("gui")) {
            guiManager.open(player, guiScreen.build(player));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                Map<String, HousePlot> available = housingService.getAvailablePlots();
                if (available.isEmpty()) {
                    messages.send(sender, "housing.no-plots-available");
                    return true;
                }
                messages.send(sender, "housing.available-header");
                available.values().forEach(plot -> messages.sendRaw(sender, "housing.plot-entry",
                        "id", plot.getId(), "name", plot.getName(), "price", MoneyFormat.format(plot.getPrice())));
            }
            case "buy" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.house-buy");
                    return true;
                }
                HousingService.ActionResult result = housingService.purchase(player, args[1]);
                if (result == HousingService.ActionResult.OK) {
                    messages.send(player, "housing.purchased", "id", args[1]);
                } else {
                    report(sender, result);
                }
            }
            case "home" -> {
                HousingService.ActionResult result = housingService.teleportHome(player);
                if (result == HousingService.ActionResult.OK) {
                    messages.send(player, "housing.teleported");
                } else {
                    report(sender, result);
                }
            }
            default -> messages.send(sender, "usage.house");
        }
        return true;
    }

    private void report(CommandSender sender, HousingService.ActionResult result) {
        if (result == HousingService.ActionResult.OK) {
            messages.send(sender, "command.ok");
            return;
        }
        String key = switch (result) {
            case PLOT_NOT_FOUND -> "housing.plot-not-found";
            case PLOT_TAKEN -> "housing.plot-taken";
            case ALREADY_OWN_A_HOUSE -> "housing.already-own-house";
            case INSUFFICIENT_FUNDS -> "housing.insufficient-funds";
            case NO_HOUSE -> "housing.no-house";
            case WORLD_NOT_FOUND -> "housing.world-not-found";
            case OK -> "command.ok";
        };
        messages.send(sender, key);
    }
}
