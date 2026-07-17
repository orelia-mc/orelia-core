package rpg.job.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
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
    private final MessageManager messages;

    public JobCommand(JobService jobService, JobManager jobManager, MessageManager messages) {
        this.jobService = jobService;
        this.jobManager = jobManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            String jobs = String.join(", ", java.util.Arrays.stream(JobType.values()).map(this::displayName).toList());
            messages.send(sender, "job.list", "jobs", jobs);
            return true;
        }

        JobType job = jobService.getCurrentJob(player.getUniqueId()).orElse(null);
        if (job == null) {
            messages.send(sender, "job.not-chosen");
            return true;
        }
        messages.send(sender, "job.current", "job", displayName(job));
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
