package rpg.extra.ranking;

import rpg.api.StatusApi;
import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import rpg.extra.ranking.command.RankingCommand;
import rpg.extra.ranking.gui.RankingGuiScreen;
import rpg.gui.framework.GuiManager;

/**
 * Ranking module: level leaderboard GUI/command (SOW RankingModule), backed entirely by
 * orelia-core's {@link StatusApi} - this module owns no data of its own.
 */
public final class RankingModule implements RpgModule {

    private RankingGuiScreen guiScreen;

    @Override
    public String getName() {
        return "ranking";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        StatusApi statusApi = plugin.getServer().getServicesManager().load(StatusApi.class);
        if (statusApi == null) {
            throw new IllegalStateException("ranking module requires OreliaCore's StatusApi");
        }

        this.guiScreen = new RankingGuiScreen(statusApi);
        RankingCommand rankingCommand = new RankingCommand(guiScreen, new GuiManager(), plugin.getMessageManager());
        String description = "レベルランキングを表示します。";
        plugin.getPlayerCommandRegistry().register("ranking", rankingCommand, description, "ranking");
        CommandAliasUtil.registerAlias(plugin, "ranking", rankingCommand, description, "");
    }

    @Override
    public void onDisable() {
    }

    public RankingGuiScreen getGuiScreen() {
        return guiScreen;
    }
}
