package rpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.boss.BossModule;
import rpg.core.OreliaPlugin;
import rpg.monster.MonsterModule;
import rpg.monster.spawnpoint.model.MonsterSpawnPoint;
import rpg.monster.spawnpoint.service.MonsterSpawnPointService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /oladmin <reload|spawn <monsterId>|spawnboss <bossId>|spawnpoint add|remove|list|...>}
 * - the root {@code /oladmin} executor. Handles orelia-core's own admin actions directly;
 * anything it doesn't recognize falls back to {@link AdminCommandRegistry}, which
 * orelia-world/orelia-extra register their own admin subcommands into (e.g.
 * {@code worldreload}) so every plugin's admin tools live under one short command instead
 * of each claiming its own top-level command name.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TOP_LEVEL_SUBCOMMANDS = List.of("reload", "spawn", "spawnboss", "spawnpoint");
    private static final String USAGE_SUFFIX = "<reload|spawn <monsterId>|spawnboss <bossId>|spawnpoint <add|remove|list> ...>";
    private static final int DEFAULT_SPAWN_POINT_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_SPAWN_POINT_MAX_ALIVE = 3;

    private final OreliaPlugin plugin;
    private final AdminCommandRegistry registry;

    public AdminCommand(OreliaPlugin plugin, AdminCommandRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " " + USAGE_SUFFIX, NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("Orelia configuration reloaded.", NamedTextColor.GREEN));
            }
            case "spawn" -> spawnMonster(sender, args);
            case "spawnboss" -> spawnBoss(sender, args);
            case "spawnpoint" -> spawnPoint(sender, args);
            default -> {
                CommandExecutor delegate = registry.get(args[0]).orElse(null);
                if (delegate == null) {
                    sender.sendMessage(Component.text("Usage: /" + label + " " + USAGE_SUFFIX, NamedTextColor.YELLOW));
                    return true;
                }
                return delegate.onCommand(sender, command, label + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<>(TOP_LEVEL_SUBCOMMANDS);
            options.addAll(registry.getNames());
            return TabCompletions.matching(options, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawnpoint")) {
            return TabCompletions.matching(List.of("add", "remove", "list"), args[1]);
        }
        CommandExecutor delegate = registry.get(args[0]).orElse(null);
        if (delegate instanceof TabCompleter completer) {
            return completer.onTabComplete(sender, command, alias + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }

    private void spawnMonster(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /oladmin spawn <monsterId>", NamedTextColor.YELLOW));
            return;
        }
        MonsterModule monsterModule = plugin.getModuleManager().get(MonsterModule.class).orElse(null);
        if (monsterModule == null) {
            sender.sendMessage(Component.text("Monster module is not enabled.", NamedTextColor.RED));
            return;
        }
        boolean spawned = monsterModule.getSpawnService().spawn(args[1], player.getLocation()).isPresent();
        sender.sendMessage(spawned ? Component.text("Spawned " + args[1] + ".", NamedTextColor.GREEN)
                : Component.text("Unknown monster id: " + args[1], NamedTextColor.RED));
    }

    private void spawnBoss(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /oladmin spawnboss <bossId>", NamedTextColor.YELLOW));
            return;
        }
        BossModule bossModule = plugin.getModuleManager().get(BossModule.class).orElse(null);
        if (bossModule == null) {
            sender.sendMessage(Component.text("Boss module is not enabled.", NamedTextColor.RED));
            return;
        }
        boolean spawned = bossModule.spawn(args[1], player.getLocation()).isPresent();
        sender.sendMessage(spawned ? Component.text("Spawned boss " + args[1] + ".", NamedTextColor.GREEN)
                : Component.text("Unknown boss id: " + args[1], NamedTextColor.RED));
    }

    private void spawnPoint(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /oladmin spawnpoint <add <monsterId> [intervalSeconds] [maxAlive]|remove <id>|list>", NamedTextColor.YELLOW));
            return;
        }
        MonsterModule monsterModule = plugin.getModuleManager().get(MonsterModule.class).orElse(null);
        if (monsterModule == null) {
            sender.sendMessage(Component.text("Monster module is not enabled.", NamedTextColor.RED));
            return;
        }
        MonsterSpawnPointService spawnPointService = monsterModule.getSpawnPointService();

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /oladmin spawnpoint add <monsterId> [intervalSeconds] [maxAlive]", NamedTextColor.YELLOW));
                    return;
                }
                int intervalSeconds = parseIntOrDefault(args, 3, DEFAULT_SPAWN_POINT_INTERVAL_SECONDS);
                int maxAlive = parseIntOrDefault(args, 4, DEFAULT_SPAWN_POINT_MAX_ALIVE);
                var created = spawnPointService.add(player, args[2], intervalSeconds, maxAlive);
                if (created.isEmpty()) {
                    sender.sendMessage(Component.text("Unknown monster id: " + args[2], NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text("Registered spawn point " + created.get().getId() + " for " + args[2]
                        + " here (every " + intervalSeconds + "s, up to " + maxAlive + " alive).", NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /oladmin spawnpoint remove <id>", NamedTextColor.YELLOW));
                    return;
                }
                try {
                    boolean removed = spawnPointService.remove(UUID.fromString(args[2]));
                    sender.sendMessage(removed ? Component.text("Removed spawn point " + args[2] + ".", NamedTextColor.GREEN)
                            : Component.text("No spawn point with id " + args[2] + ".", NamedTextColor.RED));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Not a valid spawn point id: " + args[2], NamedTextColor.RED));
                }
            }
            case "list" -> {
                var points = spawnPointService.getAll();
                if (points.isEmpty()) {
                    sender.sendMessage(Component.text("No spawn points registered.", NamedTextColor.YELLOW));
                    return;
                }
                sender.sendMessage(Component.text("Spawn points:", NamedTextColor.GREEN));
                for (MonsterSpawnPoint point : points.values()) {
                    sender.sendMessage(Component.text("- " + point.getId() + " " + point.getMonsterId()
                            + " @ " + point.getWorld() + " " + (int) point.getX() + "," + (int) point.getY() + "," + (int) point.getZ()
                            + " (" + point.getIntervalSeconds() + "s, max " + point.getMaxAlive() + ")", NamedTextColor.GRAY));
                }
            }
            default -> sender.sendMessage(Component.text("Usage: /oladmin spawnpoint <add <monsterId> [intervalSeconds] [maxAlive]|remove <id>|list>", NamedTextColor.YELLOW));
        }
    }

    private int parseIntOrDefault(String[] args, int index, int defaultValue) {
        if (args.length <= index) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
