package rpg.job.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.job.manager.JobManager;
import rpg.job.model.JobType;
import rpg.job.service.JobService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ol job <info|list>} - {@code info} prints the sender's current job, {@code list}
 * prints every job that exists. Job changes only happen through the job-change NPC, not
 * this command.
 */
public final class JobCommand implements CommandExecutor, TabCompleter {

    private final JobService jobService;
    private final JobManager jobManager;

    public JobCommand(JobService jobService, JobManager jobManager) {
        this.jobService = jobService;
        this.jobManager = jobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("Jobs: ", NamedTextColor.GREEN).append(Component.text(
                    String.join(", ", java.util.Arrays.stream(JobType.values()).map(this::displayName).toList()),
                    NamedTextColor.WHITE)));
            return true;
        }

        JobType job = jobService.getCurrentJob(player.getUniqueId()).orElse(null);
        if (job == null) {
            sender.sendMessage(Component.text("You have not chosen a job yet. Visit a job-change NPC.", NamedTextColor.YELLOW));
            return true;
        }
        sender.sendMessage(Component.text("Job: ", NamedTextColor.GREEN)
                .append(Component.text(displayName(job), NamedTextColor.WHITE)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            String prefix = (args.length == 0 ? "" : args[0]).toLowerCase();
            List<String> result = new ArrayList<>();
            if ("list".startsWith(prefix)) {
                result.add("list");
            }
            return result;
        }
        return List.of();
    }

    private String displayName(JobType type) {
        return jobManager.getDefinition(type).map(job -> job.getDisplayName()).orElse(type.name());
    }
}
