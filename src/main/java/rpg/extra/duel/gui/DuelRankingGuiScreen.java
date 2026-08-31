package rpg.extra.duel.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import rpg.extra.duel.repository.DuelStatsRepository.DuelStatsEntry;
import rpg.extra.duel.service.DuelStatsService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiPlayerHead;

import java.util.List;

/** Top-27 duel-win leaderboard - mirrors rpg.extra.ranking.gui.RankingGuiScreen's own single-flat-list shape. */
public final class DuelRankingGuiScreen {

    private final DuelStatsService statsService;

    public DuelRankingGuiScreen(DuelStatsService statsService) {
        this.statsService = statsService;
    }

    public Gui build() {
        Gui gui = new Gui("&%8決闘ランキング", 27);
        List<DuelStatsEntry> top = statsService.topByWins(27);
        int slot = 0;
        int rank = 1;
        for (DuelStatsEntry entry : top) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.uuid());
            String name = player.getName();
            String displayName = "&%e#" + rank + " " + (name != null ? name : entry.uuid());
            List<String> lore = List.of("&%a勝利: &%f" + entry.wins(), "&%c敗北: &%f" + entry.losses());
            gui.set(slot++, GuiButton.display(GuiPlayerHead.build(player, displayName, lore)));
            rank++;
        }
        if (top.isEmpty()) {
            gui.set(13, GuiButton.display(new rpg.util.ItemBuilder(Material.PAPER).name("&%7まだ決闘の記録がありません").build()));
        }
        return gui;
    }
}
