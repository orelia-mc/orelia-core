package rpg.extra.party;

import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import rpg.extra.party.command.PartyCommand;
import rpg.extra.party.listener.PartyQuitListener;
import rpg.extra.party.manager.PartyManager;
import rpg.extra.party.service.PartyService;

/**
 * Party module: runtime (not persisted) player groups - create/invite/accept/decline/leave/
 * kick/disband/transfer (SOW PartyModule).
 */
public final class PartyModule implements RpgModule {

    private final PartyManager manager = new PartyManager();
    private PartyService partyService;

    @Override
    public String getName() {
        return "party";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        int maxPartySize = plugin.getConfigManager().get("config.yml").get().getInt("party.max-size", 6);
        this.partyService = new PartyService(manager, maxPartySize);

        plugin.getServer().getPluginManager().registerEvents(
                new PartyQuitListener(manager, partyService, plugin.getMessageManager()), plugin);
        PartyCommand partyCommand = new PartyCommand(partyService, plugin.getMessageManager());
        String description = "パーティーを管理します。";
        String usage = "party <create|invite|accept|decline|leave|kick|disband|transfer|list|chat <message>>";
        plugin.getPlayerCommandRegistry().register("party", partyCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "party", partyCommand, description,
                "<create|invite|accept|decline|leave|kick|disband|transfer|list|chat <message>>");
    }

    @Override
    public void onDisable() {
    }

    public PartyService getPartyService() {
        return partyService;
    }
}
