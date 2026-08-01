package rpg.extra.ranking.gui;

import org.bukkit.Material;
import rpg.api.LeaderboardEntry;
import rpg.api.StatusApi;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.List;

/**
 * Level leaderboard screen (SOW RankingModule). Reuses orelia-core's generic {@code Gui}/
 * {@code GuiButton} framework directly; data comes from {@link StatusApi#getLeaderboard} so
 * orelia-extra never touches the status module's internals.
 */
public final class RankingGuiScreen {

    private static final Material[] RANK_ICONS = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};

    private final StatusApi statusApi;

    public RankingGuiScreen(StatusApi statusApi) {
        this.statusApi = statusApi;
    }

    public Gui build() {
        Gui gui = new Gui(ColorUtil.colorize("&%8レベルランキング"), 27);
        List<LeaderboardEntry> leaderboard = statusApi.getLeaderboard(27);

        int slot = 0;
        for (LeaderboardEntry entry : leaderboard) {
            if (slot >= 27) {
                break;
            }
            Material icon = slot < RANK_ICONS.length ? RANK_ICONS[slot] : Material.PLAYER_HEAD;
            int rank = slot + 1;
            gui.set(slot++, new GuiButton(new ItemBuilder(icon)
                    .name("&%e#" + rank + " &%f" + entry.name())
                    .lore(List.of(
                            "&%7レベル: &%f" + entry.level(),
                            "&%7経験値: &%f" + entry.experience()))
                    .build(), (clicker, clickType) -> {
            }));
        }
        return gui;
    }
}
