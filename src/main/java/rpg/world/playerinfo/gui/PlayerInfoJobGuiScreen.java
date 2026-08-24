package rpg.world.playerinfo.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.api.JobApi;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.util.ItemBuilder;

/**
 * Dedicated "ジョブ" sub-screen of the player-info nether-star menu. Opened from
 * {@link PlayerInfoGuiScreen}, which supplies the back button placed in this screen's
 * bottom-right slot.
 */
public final class PlayerInfoJobGuiScreen {

    private static final int SIZE = 36;
    private static final int JOB_SLOT = 13;
    private static final int BACK_SLOT = SIZE - 1;

    private final JobApi jobApi;

    public PlayerInfoJobGuiScreen(JobApi jobApi) {
        this.jobApi = jobApi;
    }

    public Gui build(Player player, GuiButton backButton) {
        Gui gui = new Gui("&%8プレイヤー情報 - ジョブ", SIZE);
        gui.set(BACK_SLOT, backButton);

        String jobDisplayName = jobApi.getCurrentJobDisplayName(player.getUniqueId()).orElse(null);
        gui.set(JOB_SLOT, GuiButton.display(new ItemBuilder(jobDisplayName == null ? Material.BARRIER : Material.GOLDEN_HELMET)
                .name(jobDisplayName == null ? "&%7未就業" : "&%e" + jobDisplayName)
                .build()));
        return gui;
    }
}
