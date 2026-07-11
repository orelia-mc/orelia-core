package rpg.job.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import rpg.job.model.JobType;
import rpg.job.service.JobService;

/**
 * {@code /rpgjob <info|list>} - {@code info} prints the sender's current job, {@code list}
 * prints every job that exists. Job changes only happen through the job-change NPC, not
 * this command.
 */
public final class JobCommand implements CommandExecutor {

    private final JobService jobService;

    public JobCommand(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.GREEN + "Jobs: " + ChatColor.WHITE
                    + String.join(", ", java.util.Arrays.stream(JobType.values()).map(JobType::name).toList()));
            return true;
        }

        JobType job = jobService.getCurrentJob(player.getUniqueId()).orElse(null);
        if (job == null) {
            sender.sendMessage(ChatColor.YELLOW + "You have not chosen a job yet. Visit a job-change NPC.");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Job: " + ChatColor.WHITE + job.name());
        return true;
    }
}
