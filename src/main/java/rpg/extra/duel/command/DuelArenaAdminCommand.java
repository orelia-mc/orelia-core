package rpg.extra.duel.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.duel.model.DuelArena;
import rpg.extra.duel.service.DuelArenaAdminService;

import java.util.List;

/**
 * {@code /oladmin duelarena add|set <index>|remove <index>|list} - mirrors
 * rpg.dungeon.command.DungeonArenaAdminCommand but with no dungeon-id argument, since duel
 * arenas are a single flat list.
 */
public final class DuelArenaAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("add", "set", "remove", "list");

    private final DuelArenaAdminService adminService;
    private final MessageManager messages;

    public DuelArenaAdminCommand(DuelArenaAdminService adminService, MessageManager messages) {
        this.adminService = adminService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "duel.admin.arena-usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "add" -> add(sender);
            case "set" -> set(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            default -> messages.send(sender, "duel.admin.arena-usage");
        }
        return true;
    }

    private void add(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        adminService.addArena(player.getLocation());
        messages.send(sender, "duel.admin.arena-added");
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "duel.admin.arena-usage");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "duel.admin.arena-usage");
            return;
        }
        DuelArenaAdminService.SetResult result = adminService.setArena(index, player.getLocation());
        switch (result) {
            case OK -> messages.send(sender, "duel.admin.arena-set", "index", index);
            case INDEX_OUT_OF_RANGE -> messages.send(sender, "duel.admin.arena-index-out-of-range");
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "duel.admin.arena-usage");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "duel.admin.arena-usage");
            return;
        }
        DuelArenaAdminService.RemoveResult result = adminService.removeArena(index);
        switch (result) {
            case OK -> messages.send(sender, "duel.admin.arena-removed", "index", index);
            case INDEX_OUT_OF_RANGE -> messages.send(sender, "duel.admin.arena-index-out-of-range");
        }
    }

    private void list(CommandSender sender) {
        List<DuelArena> arenas = adminService.listArenas();
        messages.send(sender, "duel.admin.arena-list-header");
        int index = 1;
        for (DuelArena arena : arenas) {
            messages.sendRaw(sender, "duel.admin.arena-list-entry",
                    "index", index, "world", arena.world(), "x", (int) arena.x(), "y", (int) arena.y(), "z", (int) arena.z());
            index++;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        return List.of();
    }
}
