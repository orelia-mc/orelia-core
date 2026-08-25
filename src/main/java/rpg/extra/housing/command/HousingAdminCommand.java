package rpg.extra.housing.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.housing.model.HousePlot;
import rpg.extra.housing.service.HousePlotAdminService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /oladmin houseplot register <id> <price> [name...]|move <id>|remove <id>|list [page]}
 * - lets an admin place/relocate/delete house plots from where they're standing instead of
 * hand-editing {@code housing.yml} and restarting the server. Named "houseplot" (not "house")
 * to avoid colliding with orelia-debug's own {@code /oladmin house} testplay command set, which
 * lives in a different plugin/repo under the same shared registry.
 */
public final class HousingAdminCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;
    private static final List<String> SUBCOMMANDS = List.of("register", "move", "remove", "list");

    private final HousePlotAdminService adminService;
    private final MessageManager messages;

    public HousingAdminCommand(HousePlotAdminService adminService, MessageManager messages) {
        this.adminService = adminService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "housing.admin.usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "register" -> register(sender, args);
            case "move" -> move(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender, args);
            default -> messages.send(sender, "housing.admin.usage");
        }
        return true;
    }

    private void register(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "housing.admin.usage-register");
            return;
        }
        String id = args[1];
        double price;
        try {
            price = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "housing.admin.usage-register");
            return;
        }
        String name = args.length >= 4 ? String.join(" ", java.util.Arrays.asList(args).subList(3, args.length)) : id;
        var registered = adminService.register(id, price, name, player.getLocation());
        if (registered.isEmpty()) {
            messages.send(sender, "housing.admin.already-exists", "id", id);
            return;
        }
        messages.send(sender, "housing.admin.registered", "id", id);
    }

    private void move(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "housing.admin.usage-move");
            return;
        }
        boolean moved = adminService.move(args[1], player.getLocation());
        messages.send(sender, moved ? "housing.admin.moved" : "housing.admin.not-found", "id", args[1]);
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "housing.admin.usage-remove");
            return;
        }
        String id = args[1];
        HousePlotAdminService.RemoveResult result = adminService.remove(id);
        switch (result) {
            case OK -> messages.send(sender, "housing.admin.removed", "id", id);
            case NOT_FOUND -> messages.send(sender, "housing.admin.not-found", "id", id);
            case OWNED -> messages.send(sender, "housing.admin.owned", "id", id);
        }
    }

    private void list(CommandSender sender, String[] args) {
        // Read fresh each call - listing is admin tooling, not hot-path, and this avoids
        // holding a second copy of plot state just for pagination.
        List<HousePlot> all = new ArrayList<>(allPlotsSortedById());
        if (all.isEmpty()) {
            messages.send(sender, "housing.admin.list-empty");
            return;
        }
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = args.length >= 2 ? parsePageOrDefault(args[1]) : 1;
        int clampedPage = Math.min(Math.max(page, 1), totalPages);
        messages.send(sender, "housing.admin.list-header", "page", clampedPage, "total", totalPages);

        int fromIndex = (clampedPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, all.size());
        for (HousePlot plot : all.subList(fromIndex, toIndex)) {
            messages.sendRaw(sender, "housing.admin.list-entry",
                    "id", plot.getId(), "name", plot.getName(), "price", (long) plot.getPrice(),
                    "world", plot.getWorld(), "x", (int) plot.getX(), "y", (int) plot.getY(), "z", (int) plot.getZ());
        }
    }

    private List<HousePlot> allPlotsSortedById() {
        List<HousePlot> plots = new ArrayList<>(adminService.list());
        plots.sort(java.util.Comparator.comparing(HousePlot::getId));
        return plots;
    }

    private int parsePageOrDefault(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        return List.of();
    }

    private List<String> matching(Iterable<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
