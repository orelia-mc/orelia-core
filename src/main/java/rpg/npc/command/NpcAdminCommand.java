package rpg.npc.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.npc.model.NpcData;
import rpg.npc.model.NpcType;
import rpg.npc.service.NpcAdminService;
import rpg.npc.service.NpcSpawnSyncService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /oladmin npc create <id> <type> [entityType]|move <id>|remove <id>|list [page]|spawnall}
 * - lets an admin place/relocate/delete NPCs from where they're standing instead of
 * hand-editing {@code npc.yml} and restarting the server, and explicitly (re-)spawns every
 * configured NPC that isn't already present via {@code spawnall} - NPCs are no longer
 * auto-spawned on startup, this is the only way to populate them.
 */
public final class NpcAdminCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;
    private static final List<String> SUBCOMMANDS = List.of("create", "move", "remove", "list", "spawnall");

    private final NpcAdminService adminService;
    private final NpcSpawnSyncService syncService;
    private final MessageManager messages;

    public NpcAdminCommand(NpcAdminService adminService, NpcSpawnSyncService syncService, MessageManager messages) {
        this.adminService = adminService;
        this.syncService = syncService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "npc.admin.usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> create(sender, args);
            case "move" -> move(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender, args);
            case "spawnall" -> spawnAll(sender);
            default -> messages.send(sender, "npc.admin.usage");
        }
        return true;
    }

    private void spawnAll(CommandSender sender) {
        syncService.syncAll();
        messages.send(sender, "npc.admin.spawned-all");
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "npc.admin.usage-create");
            return;
        }
        String id = args[1];
        NpcType type;
        try {
            type = NpcType.valueOf(args[2].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            messages.send(sender, "npc.admin.unknown-type", "type", args[2]);
            return;
        }
        EntityType entityType = EntityType.VILLAGER;
        if (args.length >= 4) {
            try {
                entityType = EntityType.valueOf(args[3].trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                messages.send(sender, "npc.admin.unknown-entity-type", "type", args[3]);
                return;
            }
        }
        var created = adminService.create(id, type, entityType, player.getLocation(), id);
        if (created.isEmpty()) {
            messages.send(sender, "npc.admin.already-exists", "id", id);
            return;
        }
        messages.send(sender, "npc.admin.created", "id", id);
    }

    private void move(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "npc.admin.usage-move");
            return;
        }
        boolean moved = adminService.move(args[1], player.getLocation());
        messages.send(sender, moved ? "npc.admin.moved" : "npc.admin.not-found", "id", args[1]);
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "npc.admin.usage-remove");
            return;
        }
        boolean removed = adminService.remove(args[1]);
        messages.send(sender, removed ? "npc.admin.removed" : "npc.admin.not-found", "id", args[1]);
    }

    private void list(CommandSender sender, String[] args) {
        List<NpcData> all = new ArrayList<>(adminService.list());
        if (all.isEmpty()) {
            messages.send(sender, "npc.admin.list-empty");
            return;
        }
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = args.length >= 2 ? parsePageOrDefault(args[1]) : 1;
        int clampedPage = Math.min(Math.max(page, 1), totalPages);
        messages.send(sender, "npc.admin.list-header", "page", clampedPage, "total", totalPages);

        int fromIndex = (clampedPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, all.size());
        for (NpcData data : all.subList(fromIndex, toIndex)) {
            messages.sendRaw(sender, "npc.admin.list-entry",
                    "id", data.getId(), "type", data.getType(), "world", data.getWorld(),
                    "x", (int) data.getX(), "y", (int) data.getY(), "z", (int) data.getZ());
        }
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
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return matching(Arrays.stream(NpcType.values()).map(Enum::name).toList(), args[2]);
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
