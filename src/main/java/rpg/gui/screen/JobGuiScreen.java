package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.job.manager.JobManager;
import rpg.job.model.JobType;
import rpg.job.service.JobService;
import rpg.util.ItemBuilder;

/**
 * Job-change screen (SOW section 17 "職業" / section 9 "職業変更はNPCから行う"). Opened by
 * the job-change NPC handler in the npc module.
 */
public final class JobGuiScreen {

    private final JobService jobService;
    private final JobManager jobManager;
    private final GuiConfig guiConfig;
    private final MessageManager messages;

    public JobGuiScreen(JobService jobService, JobManager jobManager, GuiConfig guiConfig, MessageManager messages) {
        this.jobService = jobService;
        this.jobManager = jobManager;
        this.guiConfig = guiConfig;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("job", "&%8職業変更"), 27);
        JobType current = jobService.getCurrentJob(player.getUniqueId()).orElse(null);

        int slot = 10;
        for (JobType type : JobType.values()) {
            if (slot >= 27) {
                break; // more job types configured than the screen has room for
            }
            boolean isCurrent = type == current;
            String displayName = displayName(type);
            gui.set(slot++, new GuiButton(new ItemBuilder(isCurrent ? Material.GOLDEN_HELMET : Material.LEATHER_HELMET)
                    .name((isCurrent ? "&%a" : "&%f") + displayName)
                    .lore(isCurrent ? "&%7現在の職業" : "&%7クリックで転職")
                    .build(), (clicker, clickType) -> {
                if (isCurrent) {
                    return;
                }
                boolean changed = jobService.changeJob(clicker.getUniqueId(), type);
                if (changed) {
                    messages.send(clicker, "job.changed", "job", displayName);
                    clicker.closeInventory();
                } else {
                    messages.send(clicker, "job.change-failed");
                }
            }));
        }
        return gui;
    }

    private String displayName(JobType type) {
        return jobManager.getDefinition(type).map(job -> job.getDisplayName()).orElse(type.name());
    }
}
