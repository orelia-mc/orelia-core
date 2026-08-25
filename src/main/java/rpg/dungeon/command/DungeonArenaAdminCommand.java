package rpg.dungeon.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.dungeon.model.DungeonArena;
import rpg.dungeon.repository.DungeonRepository;
import rpg.dungeon.service.DungeonArenaAdminService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /oladmin dungeonarena add <dungeon-id>|remove <dungeon-id> <index>|list <dungeon-id>}
 * - registers/removes a dungeon's physical entry point(s) from where the admin is standing.
 * Named "dungeonarena" (not "dungeon") to avoid colliding with orelia-debug's own
 * {@code /oladmin dungeon} testplay command set, and distinct from the existing
 * {@code /oladmin dungeonblock} (which places the physical unlock-trigger block, a different
 * concept from registering the dungeon's own arena/entry location).
 */
public final class DungeonArenaAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "list");

    private final DungeonArenaAdminService adminService;
    private final DungeonRepository dungeonRepository;
    private final MessageManager messages;

    public DungeonArenaAdminCommand(DungeonArenaAdminService adminService, DungeonRepository dungeonRepository, MessageManager messages) {
        this.adminService = adminService;
        this.dungeonRepository = dungeonRepository;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "dungeon.admin.arena-usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "add" -> add(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender, args);
            default -> messages.send(sender, "dungeon.admin.arena-usage");
        }
        return true;
    }

    private void add(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "dungeon.admin.arena-usage");
            return;
        }
        String dungeonId = args[1];
        var added = adminService.addArena(dungeonId, player.getLocation());
        if (added.isEmpty()) {
            messages.send(sender, "dungeon.admin.unknown-dungeon", "id", dungeonId);
            return;
        }
        messages.send(sender, "dungeon.admin.arena-added", "dungeon", dungeonId);
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "dungeon.admin.arena-usage");
            return;
        }
        String dungeonId = args[1];
        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "dungeon.admin.arena-usage");
            return;
        }
        DungeonArenaAdminService.RemoveResult result = adminService.removeArena(dungeonId, index);
        switch (result) {
            case OK -> messages.send(sender, "dungeon.admin.arena-removed", "dungeon", dungeonId, "index", index);
            case DUNGEON_NOT_FOUND -> messages.send(sender, "dungeon.admin.unknown-dungeon", "id", dungeonId);
            case INDEX_OUT_OF_RANGE -> messages.send(sender, "dungeon.admin.arena-index-out-of-range");
            case LAST_ARENA -> messages.send(sender, "dungeon.admin.arena-last-one");
        }
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "dungeon.admin.arena-usage");
            return;
        }
        String dungeonId = args[1];
        if (dungeonRepository.findById(dungeonId).isEmpty()) {
            messages.send(sender, "dungeon.admin.unknown-dungeon", "id", dungeonId);
            return;
        }
        List<DungeonArena> arenas = adminService.listArenas(dungeonId);
        messages.send(sender, "dungeon.admin.arena-list-header", "dungeon", dungeonId);
        int index = 1;
        for (DungeonArena arena : arenas) {
            messages.sendRaw(sender, "dungeon.admin.arena-list-entry",
                    "index", index, "world", arena.world(), "x", (int) arena.x(), "y", (int) arena.y(), "z", (int) arena.z());
            index++;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("list"))) {
            return matching(dungeonRepository.getAll().keySet(), args[1]);
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
