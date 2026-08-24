package rpg.extra.friend;

import rpg.core.command.CommandAliasUtil;
import rpg.database.manager.DatabaseManager;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.friend.command.FriendCommand;
import rpg.extra.friend.listener.FriendQuitListener;
import rpg.extra.friend.manager.FriendRequestManager;
import rpg.extra.friend.manager.TeleportRequestManager;
import rpg.extra.friend.repository.FriendRepository;
import rpg.extra.friend.service.FriendService;
import rpg.extra.friend.service.FriendTeleportService;

import java.util.logging.Level;

/**
 * Friend module: persistent mutual friend list, plus friend-only teleport requests (SOW
 * follow-up "フレンド機能"). Registered right after {@link rpg.extra.party.PartyModule} -
 * both are runtime social features, but unlike Party this one persists across restarts.
 */
public final class FriendModule implements RpgModule {

    private FriendService friendService;
    private FriendTeleportService teleportService;

    @Override
    public String getName() {
        return "friend";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("friend module requires OreliaCore's DatabaseManager");
        }

        FriendRepository repository = new FriendRepository(databaseManager);
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize friend schema", e);
        }

        int maxFriends = plugin.getConfigManager().get("config.yml").get().getInt("friend.max-friends", 50);
        FriendRequestManager requestManager = new FriendRequestManager();
        TeleportRequestManager teleportRequestManager = new TeleportRequestManager();
        this.friendService = new FriendService(repository, requestManager, maxFriends);
        this.teleportService = new FriendTeleportService(teleportRequestManager, friendService);

        plugin.getServer().getPluginManager().registerEvents(
                new FriendQuitListener(requestManager, teleportRequestManager, plugin.getMessageManager()), plugin);

        FriendCommand friendCommand = new FriendCommand(friendService, teleportService, plugin.getMessageManager());
        String description = "フレンドを管理します。";
        String usage = "friend <add|accept|decline|remove|list|tpa|tpaccept|tpdecline>";
        plugin.getPlayerCommandRegistry().register("friend", friendCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "friend", friendCommand, description,
                "<add|accept|decline|remove|list|tpa|tpaccept|tpdecline>");
    }

    @Override
    public void onDisable() {
    }

    public FriendService getFriendService() {
        return friendService;
    }

    public FriendTeleportService getTeleportService() {
        return teleportService;
    }
}
