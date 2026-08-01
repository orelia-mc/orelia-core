package rpg.dungeon.command;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.dungeon.model.DungeonBlockKey;
import rpg.dungeon.repository.DungeonBlockRepository;
import rpg.dungeon.repository.DungeonRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /oladmin dungeonblock set <dungeon-id>|remove|list [page]} - registers/clears the
 * admin's currently-targeted block as a dungeon's trigger. Named "dungeonblock" (not
 * "dungeon") to avoid colliding with orelia-debug's own {@code /oladmin dungeon} testplay
 * command set, which lives in a different plugin/repo under the same shared registry.
 */
public final class DungeonAdminCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;
    private static final List<String> SUBCOMMANDS = List.of("set", "remove", "list");
    private static final int TARGET_BLOCK_RANGE = 5;

    private final DungeonBlockRepository blockRepository;
    private final DungeonRepository dungeonRepository;
    private final MessageManager messages;

    public DungeonAdminCommand(DungeonBlockRepository blockRepository, DungeonRepository dungeonRepository, MessageManager messages) {
        this.blockRepository = blockRepository;
        this.dungeonRepository = dungeonRepository;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "dungeon.admin.usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "set" -> set(sender, args);
            case "remove" -> remove(sender);
            case "list" -> list(sender, args);
            default -> messages.send(sender, "dungeon.admin.usage");
        }
        return true;
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "dungeon.admin.usage-set");
            return;
        }
        String dungeonId = args[1];
        if (dungeonRepository.findById(dungeonId).isEmpty()) {
            messages.send(sender, "dungeon.admin.unknown-dungeon", "id", dungeonId);
            return;
        }
        Block target = player.getTargetBlockExact(TARGET_BLOCK_RANGE);
        if (target == null) {
            messages.send(sender, "dungeon.admin.no-target-block");
            return;
        }
        blockRepository.place(target.getLocation(), dungeonId);
        messages.send(sender, "dungeon.admin.block-set", "dungeon", dungeonId);
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        Block target = player.getTargetBlockExact(TARGET_BLOCK_RANGE);
        if (target == null) {
            messages.send(sender, "dungeon.admin.no-target-block");
            return;
        }
        boolean removed = blockRepository.remove(target.getLocation());
        messages.send(sender, removed ? "dungeon.admin.block-removed" : "dungeon.admin.no-block-registered");
    }

    private void list(CommandSender sender, String[] args) {
        List<Map.Entry<DungeonBlockKey, String>> all = new ArrayList<>(blockRepository.getAll().entrySet());
        if (all.isEmpty()) {
            messages.send(sender, "dungeon.admin.list-empty");
            return;
        }
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = args.length >= 2 ? parsePageOrDefault(args[1]) : 1;
        int clampedPage = Math.min(Math.max(page, 1), totalPages);
        messages.send(sender, "dungeon.admin.list-header", "page", clampedPage, "total", totalPages);

        int fromIndex = (clampedPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, all.size());
        for (Map.Entry<DungeonBlockKey, String> entry : all.subList(fromIndex, toIndex)) {
            DungeonBlockKey key = entry.getKey();
            messages.sendRaw(sender, "dungeon.admin.list-entry",
                    "dungeon", entry.getValue(), "world", key.world(), "x", key.x(), "y", key.y(), "z", key.z());
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
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
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
