package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.gui.framework.GuiPageLayout;
import rpg.gui.framework.GuiPaginator;
import rpg.job.manager.JobManager;
import rpg.job.model.JobType;
import rpg.job.service.JobService;
import rpg.util.ItemBuilder;

import java.util.List;

/**
 * Job-change screen (SOW section 17 "職業" / section 9 "職業変更はNPCから行う"). Opened by
 * the job-change NPC handler in the npc module.
 */
public final class JobGuiScreen {

    private static final GuiPageLayout LAYOUT =
            new GuiPageLayout(new int[] {10, 11, 12, 13, 14, 15, 16}, 18, 26);

    private final JobService jobService;
    private final JobManager jobManager;
    private final GuiConfig guiConfig;
    private final MessageManager messages;
    private final GuiManager guiManager;

    public JobGuiScreen(JobService jobService, JobManager jobManager, GuiConfig guiConfig, MessageManager messages,
                         GuiManager guiManager) {
        this.jobService = jobService;
        this.jobManager = jobManager;
        this.guiConfig = guiConfig;
        this.messages = messages;
        this.guiManager = guiManager;
    }

    public Gui build(Player player) {
        return build(player, 0);
    }

    private Gui build(Player player, int page) {
        Gui gui = new Gui(guiConfig.title("job", "&%8職業変更"), 27);
        JobType current = jobService.getCurrentJob(player.getUniqueId()).orElse(null);

        GuiPaginator.placePage(guiManager, gui, LAYOUT, List.of(JobType.values()), page,
                type -> jobButton(type, current), p -> build(player, p));
        return gui;
    }

    private GuiButton jobButton(JobType type, JobType current) {
        boolean isCurrent = type == current;
        String displayName = displayName(type);
        return new GuiButton(new ItemBuilder(isCurrent ? Material.GOLDEN_HELMET : Material.LEATHER_HELMET)
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
        });
    }

    private String displayName(JobType type) {
        return jobManager.getDefinition(type).map(job -> job.getDisplayName()).orElse(type.name());
    }
}
