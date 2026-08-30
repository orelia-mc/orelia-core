package rpg.job.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import rpg.api.GuiApi;
import rpg.core.message.MessageManager;
import rpg.job.manager.JobManager;
import rpg.job.model.JobType;
import rpg.job.service.JobService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ol job [gui|info|list]} - bare {@code job} (or explicit {@code gui}) opens the same
 * job-change screen the {@code JOB_CHANGE} NPC does, {@code info} prints the sender's current
 * job as chat text, {@code list} prints every job that exists. The NPC remains a valid way to
 * reach the same screen - this command is an additional entry point, not a replacement.
 *
 * <p>{@link GuiApi} is resolved lazily via {@link ServicesManager#load} inside {@code onCommand}
 * rather than once at construction time - {@code JobModule} registers well before {@code GuiModule}/
 * {@code ApiModule} in the fixed module order (foundation block: Job comes right after Status,
 * Gui/Api come near the end of that same block), so a lookup at construction would always resolve
 * {@code null} even though {@code GuiApi} is fully published by the time a player actually types
 * this command. Same lazy-lookup reasoning {@code PlayerInfoGuiScreen}'s own doc comment gives for
 * its {@code achievementApi} field.
 */
public final class JobCommand implements CommandExecutor, TabCompleter {

    private final JobService jobService;
    private final JobManager jobManager;
    private final MessageManager messages;
    private final ServicesManager services;

    public JobCommand(JobService jobService, JobManager jobManager, MessageManager messages, ServicesManager services) {
        this.jobService = jobService;
        this.jobManager = jobManager;
        this.messages = messages;
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            // GuiApi is core's own foundation-layer service (ApiModule, same jar) - unlike a
            // soft dependency on another plugin, it's guaranteed published by the time any
            // player command actually runs, so a missing service here is a real startup-order
            // bug worth failing loudly on rather than a normal "not installed" case to message
            // the player about.
            GuiApi guiApi = services.load(GuiApi.class);
            if (guiApi == null) {
                throw new IllegalStateException("GuiApi not published - ApiModule should have registered it before any player command can run");
            }
            guiApi.openJobChange(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            String jobs = String.join(", ", java.util.Arrays.stream(JobType.values()).map(this::displayName).toList());
            messages.send(sender, "job.list", "jobs", jobs);
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            JobType job = jobService.getCurrentJob(player.getUniqueId()).orElse(null);
            if (job == null) {
                messages.send(sender, "job.not-chosen");
                return true;
            }
            messages.send(sender, "job.current", "job", displayName(job));
            return true;
        }
        messages.send(sender, "usage.job");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            String prefix = (args.length == 0 ? "" : args[0]).toLowerCase();
            List<String> result = new ArrayList<>();
            for (String sub : List.of("gui", "info", "list")) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
            return result;
        }
        return List.of();
    }

    private String displayName(JobType type) {
        return jobManager.getDefinition(type).map(job -> job.getDisplayName()).orElse(type.name());
    }
}
