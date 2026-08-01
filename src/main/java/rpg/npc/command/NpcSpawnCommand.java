package rpg.npc.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.npc.repository.NpcRepository;
import rpg.npc.service.NpcSpawnService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /oladmin spawnnpc <npc-id>} - spawns any npc.yml-defined NPC at the sender's
 * current location. This is the only way to place NPC types excluded from
 * {@code NpcSpawnSyncService}'s automatic startup sync (currently just the job-change NPC),
 * but it accepts any configured npc.yml id.
 */
public final class NpcSpawnCommand implements CommandExecutor, TabCompleter {

    private final NpcRepository repository;
    private final NpcSpawnService spawnService;

    public NpcSpawnCommand(NpcRepository repository, NpcSpawnService spawnService) {
        this.repository = repository;
        this.spawnService = spawnService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <npc-id>");
            return true;
        }
        boolean spawned = spawnService.spawn(args[0], player.getLocation()).isPresent();
        sender.sendMessage(spawned ? ChatColor.GREEN + "Spawned NPC " + args[0] + "."
                : ChatColor.RED + "Unknown npc id: " + args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return matching(repository.getAll().keySet(), args.length == 0 ? "" : args[0]);
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
