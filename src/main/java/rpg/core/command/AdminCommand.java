package rpg.core.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.boss.BossModule;
import rpg.core.OreliaPlugin;
import rpg.monster.MonsterModule;

/**
 * {@code /rpgadmin <reload|spawn <monsterId>|spawnboss <bossId>>}. Individual module
 * commands (item, job, quest, ...) live in their own module's {@code command} package.
 *
 * <p>{@code spawn}/{@code spawnboss} exist because nothing else in orelia-core/orelia-world
 * currently spawns monsters (no natural-world hook, no dungeon integration yet) - without
 * this, monsters can never appear at all, which blocks combat, leveling, and any
 * KILL_MONSTER quest objective. Treat this as a QA tool until real spawning is designed.
 */
public final class AdminCommand implements CommandExecutor {

    private final OreliaPlugin plugin;

    public AdminCommand(OreliaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /rpgadmin <reload|spawn <monsterId>|spawnboss <bossId>>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "Orelia configuration reloaded.");
            }
            case "spawn" -> spawnMonster(sender, args);
            case "spawnboss" -> spawnBoss(sender, args);
            default -> sender.sendMessage(ChatColor.YELLOW + "Usage: /rpgadmin <reload|spawn <monsterId>|spawnboss <bossId>>");
        }
        return true;
    }

    private void spawnMonster(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /rpgadmin spawn <monsterId>");
            return;
        }
        MonsterModule monsterModule = plugin.getModuleManager().get(MonsterModule.class).orElse(null);
        if (monsterModule == null) {
            sender.sendMessage(ChatColor.RED + "Monster module is not enabled.");
            return;
        }
        boolean spawned = monsterModule.getSpawnService().spawn(args[1], player.getLocation()).isPresent();
        sender.sendMessage(spawned ? ChatColor.GREEN + "Spawned " + args[1] + "."
                : ChatColor.RED + "Unknown monster id: " + args[1]);
    }

    private void spawnBoss(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /rpgadmin spawnboss <bossId>");
            return;
        }
        BossModule bossModule = plugin.getModuleManager().get(BossModule.class).orElse(null);
        if (bossModule == null) {
            sender.sendMessage(ChatColor.RED + "Boss module is not enabled.");
            return;
        }
        boolean spawned = bossModule.spawn(args[1], player.getLocation()).isPresent();
        sender.sendMessage(spawned ? ChatColor.GREEN + "Spawned boss " + args[1] + "."
                : ChatColor.RED + "Unknown boss id: " + args[1]);
    }
}
