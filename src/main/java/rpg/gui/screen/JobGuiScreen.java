package rpg.gui.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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

    public JobGuiScreen(JobService jobService, JobManager jobManager, GuiConfig guiConfig) {
        this.jobService = jobService;
        this.jobManager = jobManager;
        this.guiConfig = guiConfig;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("job", "&8職業変更"), 27);
        JobType current = jobService.getCurrentJob(player.getUniqueId()).orElse(null);

        int slot = 10;
        for (JobType type : JobType.values()) {
            boolean isCurrent = type == current;
            String displayName = displayName(type);
            gui.set(slot++, new GuiButton(new ItemBuilder(isCurrent ? Material.GOLDEN_HELMET : Material.LEATHER_HELMET)
                    .name((isCurrent ? "&a" : "&f") + displayName)
                    .lore(isCurrent ? "&7現在の職業" : "&7クリックで転職")
                    .build(), (clicker, clickType) -> {
                if (isCurrent) {
                    return;
                }
                boolean changed = jobService.changeJob(clicker.getUniqueId(), type);
                clicker.sendMessage(changed ? Component.text(displayName + "に転職しました。", NamedTextColor.GREEN)
                        : Component.text("転職に失敗しました。", NamedTextColor.RED));
                if (changed) {
                    clicker.closeInventory();
                }
            }));
        }
        return gui;
    }

    private String displayName(JobType type) {
        return jobManager.getDefinition(type).map(job -> job.getDisplayName()).orElse(type.name());
    }
}
