package rpg.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.boss.BossModule;
import rpg.core.OreliaPlugin;
import rpg.core.message.MessageManager;
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
    // Built-in subcommands are hardcoded in this class rather than going through
    // AdminCommandRegistry, so they need their own Entry list to show up in "/oladmin help"
    // alongside whatever orelia-world/orelia-extra/orelia-debug register.
    private static final List<OlCommandRegistry.Entry> BUILTIN_ENTRIES = List.of(
            new OlCommandRegistry.Entry("reload", null, "全モジュールの設定を再読み込みします。", "reload"),
            new OlCommandRegistry.Entry("spawn", null, "自分の足元にモンスターを湧かせます。", "spawn <monsterId>"),
            new OlCommandRegistry.Entry("spawnboss", null, "自分の足元にボスを湧かせます。", "spawnboss <bossId>"),
            new OlCommandRegistry.Entry("spawnpoint", null, "モンスターの自動湧きポイントを管理します。", "spawnpoint <add|remove|list> ...")
    );
    private static final int DEFAULT_SPAWN_POINT_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_SPAWN_POINT_MAX_ALIVE = 3;

    private final OreliaPlugin plugin;
    private final AdminCommandRegistry registry;
    private final MessageManager messages;

    public AdminCommand(OreliaPlugin plugin, AdminCommandRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            int page = args.length >= 2 ? parsePageOrDefault(args[1]) : 1;
            List<OlCommandRegistry.Entry> entries = new ArrayList<>(BUILTIN_ENTRIES);
            entries.addAll(registry.getEntries());
            CommandHelpUtil.sendHelp(sender, label, entries, page);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                messages.send(sender, "admin.reloaded");
            }
            case "spawn" -> spawnMonster(sender, args);
            case "spawnboss" -> spawnBoss(sender, args);
            case "spawnpoint" -> spawnPoint(sender, args);
            default -> {
                CommandExecutor delegate = registry.get(args[0]).orElse(null);
                if (delegate == null) {
                    messages.send(sender, "command.unknown-subcommand", "name", args[0], "label", label);
                    return true;
                }
                return delegate.onCommand(sender, command, label + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return true;
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
            List<String> options = new ArrayList<>(TOP_LEVEL_SUBCOMMANDS);
            options.add("help");
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
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "admin.usage-spawn");
            return;
        }
        MonsterModule monsterModule = plugin.getModuleManager().get(MonsterModule.class).orElse(null);
        if (monsterModule == null) {
            messages.send(sender, "admin.module-disabled", "module", "Monster");
            return;
        }
        boolean spawned = monsterModule.getSpawnService().spawn(args[1], player.getLocation()).isPresent();
        if (spawned) {
            messages.send(sender, "admin.spawned-monster", "id", args[1]);
        } else {
            messages.send(sender, "admin.unknown-monster", "id", args[1]);
        }
    }

    private void spawnBoss(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "admin.usage-spawnboss");
            return;
        }
        BossModule bossModule = plugin.getModuleManager().get(BossModule.class).orElse(null);
        if (bossModule == null) {
            messages.send(sender, "admin.module-disabled", "module", "Boss");
            return;
        }
        boolean spawned = bossModule.spawn(args[1], player.getLocation()).isPresent();
        if (spawned) {
            messages.send(sender, "admin.spawned-boss", "id", args[1]);
        } else {
            messages.send(sender, "admin.unknown-boss", "id", args[1]);
        }
    }

    private void spawnPoint(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "admin.usage-spawnpoint");
            return;
        }
        MonsterModule monsterModule = plugin.getModuleManager().get(MonsterModule.class).orElse(null);
        if (monsterModule == null) {
            messages.send(sender, "admin.module-disabled", "module", "Monster");
            return;
        }
        MonsterSpawnPointService spawnPointService = monsterModule.getSpawnPointService();

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "command.player-only");
                    return;
                }
                if (args.length < 3) {
                    messages.send(sender, "admin.usage-spawnpoint-add");
                    return;
                }
                int intervalSeconds = parseIntOrDefault(args, 3, DEFAULT_SPAWN_POINT_INTERVAL_SECONDS);
                int maxAlive = parseIntOrDefault(args, 4, DEFAULT_SPAWN_POINT_MAX_ALIVE);
                var created = spawnPointService.add(player, args[2], intervalSeconds, maxAlive);
                if (created.isEmpty()) {
                    messages.send(sender, "admin.unknown-monster", "id", args[2]);
                    return;
                }
                messages.send(sender, "admin.spawnpoint-registered",
                        "id", created.get().getId(), "monster", args[2], "interval", intervalSeconds, "max", maxAlive);
            }
            case "remove" -> {
                if (args.length < 3) {
                    messages.send(sender, "admin.usage-spawnpoint-remove");
                    return;
                }
                try {
                    boolean removed = spawnPointService.remove(UUID.fromString(args[2]));
                    if (removed) {
                        messages.send(sender, "admin.spawnpoint-removed", "id", args[2]);
                    } else {
                        messages.send(sender, "admin.spawnpoint-not-found", "id", args[2]);
                    }
                } catch (IllegalArgumentException e) {
                    messages.send(sender, "admin.spawnpoint-invalid-id", "id", args[2]);
                }
            }
            case "list" -> {
                var points = spawnPointService.getAll();
                if (points.isEmpty()) {
                    messages.send(sender, "admin.spawnpoint-empty");
                    return;
                }
                messages.send(sender, "admin.spawnpoint-list-header");
                for (MonsterSpawnPoint point : points.values()) {
                    messages.sendRaw(sender, "admin.spawnpoint-list-entry",
                            "id", point.getId(), "monster", point.getMonsterId(), "world", point.getWorld(),
                            "x", (int) point.getX(), "y", (int) point.getY(), "z", (int) point.getZ(),
                            "interval", point.getIntervalSeconds(), "max", point.getMaxAlive());
                }
            }
            default -> messages.send(sender, "admin.usage-spawnpoint");
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
