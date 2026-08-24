package rpg.extra.party;

import rpg.core.command.CommandAliasUtil;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.party.command.PartyCommand;
import rpg.extra.party.gui.PartyGuiScreen;
import rpg.extra.party.listener.PartyQuitListener;
import rpg.extra.party.manager.PartyManager;
import rpg.extra.party.service.PartyService;
import rpg.gui.framework.GuiManager;

/**
 * Party module: runtime (not persisted) player groups - create/invite/accept/decline/leave/
 * kick/disband/transfer (SOW PartyModule).
 */
public final class PartyModule implements RpgModule {

    private final PartyManager manager = new PartyManager();
    private PartyService partyService;
    private PartyGuiScreen partyGuiScreen;
    private GuiManager guiManager;

    @Override
    public String getName() {
        return "party";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        int maxPartySize = plugin.getConfigManager().get("config.yml").get().getInt("party.max-size", 6);
        this.partyService = new PartyService(manager, maxPartySize);
        this.guiManager = new GuiManager();
        this.partyGuiScreen = new PartyGuiScreen(partyService, guiManager, plugin.getChatInputService(), plugin.getMessageManager());

        plugin.getServer().getPluginManager().registerEvents(
                new PartyQuitListener(manager, partyService, plugin.getMessageManager()), plugin);
        PartyCommand partyCommand = new PartyCommand(partyService, plugin.getMessageManager(), plugin.getChatMuteService(),
                plugin.getConfigManager(), plugin.getLogger(), partyGuiScreen, guiManager);
        String description = "パーティーを管理します。";
        String usage = "party <create|invite|accept|decline|leave|kick|disband|transfer|list|gui|chat <message>>";
        plugin.getPlayerCommandRegistry().register("party", partyCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "party", partyCommand, description,
                "<create|invite|accept|decline|leave|kick|disband|transfer|list|gui|chat <message>>");
    }

    @Override
    public void onDisable() {
    }

    public PartyService getPartyService() {
        return partyService;
    }

    public PartyGuiScreen getPartyGuiScreen() {
        return partyGuiScreen;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }
}
