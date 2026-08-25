package rpg.core;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import rpg.core.command.AdminCommand;
import rpg.core.command.AdminCommandRegistry;
import rpg.core.command.OlRootCommand;
import rpg.core.command.PlayerCommandRegistry;
import rpg.core.chat.ChatInputListener;
import rpg.core.chat.ChatInputService;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.core.config.LegacyDataFolderMigrator;

import java.util.List;
import rpg.core.listener.PlayerConnectionListener;
import rpg.core.message.MessageManager;
import rpg.core.module.ModuleManager;
import rpg.core.player.PlayerDataManager;
import rpg.core.scheduler.SchedulerService;
import rpg.database.DatabaseModule;
import rpg.status.StatusModule;
import rpg.job.JobModule;
import rpg.gathering.GatheringModule;
import rpg.item.ItemModule;
import rpg.skill.SkillModule;
import rpg.accessory.AccessoryModule;
import rpg.effect.EffectModule;
import rpg.economy.EconomyModule;
import rpg.monster.MonsterModule;
import rpg.boss.BossModule;
import rpg.gui.GuiModule;
import rpg.region.RegionModule;
import rpg.town.TownModule;
import rpg.api.ApiModule;
import rpg.world.dialogue.DialogueModule;
import rpg.world.story.StoryModule;
import rpg.world.event.EventModule;
import rpg.world.cutscene.CutSceneModule;
import rpg.quest.QuestModule;
import rpg.dungeon.DungeonModule;
import rpg.npc.NpcModule;
import rpg.world.playerinfo.PlayerInfoModule;
import rpg.world.api.WorldApiModule;
import rpg.extra.party.PartyModule;
import rpg.extra.friend.FriendModule;
import rpg.extra.guild.GuildModule;
import rpg.extra.chat.ChatModule;
import rpg.extra.trade.TradeModule;
import rpg.extra.mail.MailModule;
import rpg.extra.auction.AuctionModule;
import rpg.extra.housing.HousingModule;
import rpg.extra.pet.PetModule;
import rpg.extra.mount.MountModule;
import rpg.extra.ranking.RankingModule;
import rpg.extra.achievement.AchievementModule;
import rpg.extra.api.ExtraApiModule;
import rpg.extra.chat.service.ChatMuteService;

/**
 * Plugin entry point for the orelia-core repo/jar. Owns process-wide singletons (config,
 * player data, scheduler, module registry) and wires every top-level Module in dependency
 * order. No gameplay logic lives here; see the individual module packages.
 *
 * <p>As of the orelia-core/orelia-world/orelia-extra merge, this single plugin owns the
 * foundation (combat/player/status), content (quest/NPC/dialogue/story/dungeon/cutscene/
 * event), and social/economy (party/guild/trade/mail/auction/housing/pet/mount/ranking/
 * achievement) layers in one jar/classloader. The former inter-plugin {@code rpg.api}/
 * {@code rpg.world.api}/{@code rpg.extra.api} facades are kept and still published via
 * Bukkit's {@code ServicesManager} - genuinely external plugins (e.g. orelia-debug) still
 * depend on that publication - but modules in this jar no longer need to guard against
 * "is the other plugin installed/enabled yet" races, since everything enables in one
 * deterministic order below.
 */
public final class OreliaPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private SchedulerService schedulerService;
    private PlayerDataManager playerDataManager;
    private ModuleManager moduleManager;
    private PlayerCommandRegistry playerCommandRegistry;
    private AdminCommandRegistry adminCommandRegistry;
    private ChatMuteService chatMuteService;
    private ChatInputService chatInputService;

    @Override
    public void onEnable() {
        // Runs before any config file is registered: pulls an operator's customized yml files
        // out of the former plugins' data folders, since the merge moved every config here.
        LegacyDataFolderMigrator.migrate(getLogger(), getDataFolder());

        this.configManager = new ConfigManager(this);
        ConfigFile config = this.configManager.register("config.yml");
        this.messageManager = new MessageManager(configManager.register("messages.yml"));

        this.schedulerService = new SchedulerService(this);
        this.playerDataManager = new PlayerDataManager(getLogger(), schedulerService);
        this.moduleManager = new ModuleManager(this);
        // Built here (not inside ChatModule) so PartyModule/GuildModule - registered before
        // ChatModule since ChatModule needs their services already built - can reach it too;
        // ChatMuteService itself has no dependency on any module, so there's no ordering issue
        // giving every module the same shared instance from the start. chat.mute.enabled is a
        // kill switch (default true) - flip it off in config.yml to disable /chat mute entirely
        // without removing the feature.
        this.chatMuteService = new ChatMuteService(config.get().getBoolean("chat.mute.enabled", true));
        // Shared the same way as chatMuteService above - any module's GUI screen may want to
        // prompt for free-text chat input (guild/party name, a player name, ...).
        this.chatInputService = new ChatInputService(schedulerService, messageManager);
        getServer().getPluginManager().registerEvents(new ChatInputListener(chatInputService), this);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(playerDataManager), this);

        // Published so orelia-world/orelia-extra can register their own subcommands into
        // these same two short entry points instead of each claiming a top-level command.
        this.playerCommandRegistry = new PlayerCommandRegistry();
        this.adminCommandRegistry = new AdminCommandRegistry();
        getServer().getServicesManager().register(PlayerCommandRegistry.class, playerCommandRegistry, this, ServicePriority.Normal);
        getServer().getServicesManager().register(AdminCommandRegistry.class, adminCommandRegistry, this, ServicePriority.Normal);

        OlRootCommand olRootCommand = new OlRootCommand(playerCommandRegistry);
        getCommand("ol").setExecutor(olRootCommand);
        getCommand("ol").setTabCompleter(olRootCommand);

        AdminCommand adminCommand = new AdminCommand(this, adminCommandRegistry);
        getCommand("oladmin").setExecutor(adminCommand);
        getCommand("oladmin").setTabCompleter(adminCommand);

        // Registration order doubles as dependency order: later modules may look up
        // earlier ones via ModuleManager#get, never the reverse. Api/WorldApi/ExtraApi are
        // each registered right after the last module in their own former-plugin block so
        // that block's services are fully constructed before it publishes anything, while
        // the next block (which may depend on a *Api of an earlier block, e.g. Achievement
        // needing QuestApi) still sees a deterministic, fully-built dependency.
        moduleManager.register(new DatabaseModule());
        // Registered early (no dependency of its own) since GatheringModule's fishing area
        // detection needs its RegionQueryService before TownModule ever exists.
        moduleManager.register(new RegionModule());
        moduleManager.register(new StatusModule());
        moduleManager.register(new JobModule());
        moduleManager.register(new GatheringModule());
        moduleManager.register(new ItemModule());
        moduleManager.register(new SkillModule());
        moduleManager.register(new EffectModule());
        moduleManager.register(new EconomyModule());
        // Registered after EconomyModule (not alphabetically) - relics' upgrade cost needs
        // Vault's Economy, which EconomyModule only registers with Vault once it enables.
        moduleManager.register(new AccessoryModule());
        // Registered right before MonsterModule - spawn suppression inside towns needs
        // TownDetectionService already built.
        moduleManager.register(new TownModule());
        moduleManager.register(new MonsterModule());
        moduleManager.register(new BossModule());
        moduleManager.register(new GuiModule());
        moduleManager.register(new ApiModule());

        // --- former orelia-world content layer ---
        moduleManager.register(new DialogueModule());
        moduleManager.register(new StoryModule());
        moduleManager.register(new EventModule());
        moduleManager.register(new CutSceneModule());
        // Quest before Dungeon (not alphabetical): DungeonEncounterService calls
        // QuestProgressService#onDungeonCleared, so QuestModule must already be enabled.
        moduleManager.register(new QuestModule());
        moduleManager.register(new DungeonModule());
        moduleManager.register(new NpcModule());
        moduleManager.register(new PlayerInfoModule());
        moduleManager.register(new WorldApiModule());

        // --- former orelia-extra social/economy layer ---
        // Modules with no dependency on each other register in roughly alphabetical order;
        // Ranking/Achievement register last since they read state produced by earlier
        // modules rather than owning anything themselves.
        moduleManager.register(new PartyModule());
        moduleManager.register(new FriendModule());
        moduleManager.register(new GuildModule());
        moduleManager.register(new ChatModule());
        moduleManager.register(new TradeModule());
        moduleManager.register(new MailModule());
        moduleManager.register(new AuctionModule());
        moduleManager.register(new HousingModule());
        moduleManager.register(new PetModule());
        moduleManager.register(new MountModule());
        moduleManager.register(new RankingModule());
        moduleManager.register(new AchievementModule());
        moduleManager.register(new ExtraApiModule());

        moduleManager.enableAll();
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllOnlineSync();
        }
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
    }

    /** @return every config file's leaf-key changes from this reload (see {@link ConfigManager#reloadAllWithDiff}) - empty if nothing on disk actually changed. */
    public List<ConfigManager.FileDiff> reload() {
        java.util.List<ConfigManager.FileDiff> diffs = configManager.reloadAllWithDiff();
        moduleManager.reloadAll();
        return diffs;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public SchedulerService getSchedulerService() {
        return schedulerService;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public PlayerCommandRegistry getPlayerCommandRegistry() {
        return playerCommandRegistry;
    }

    public AdminCommandRegistry getAdminCommandRegistry() {
        return adminCommandRegistry;
    }

    public ChatMuteService getChatMuteService() {
        return chatMuteService;
    }

    public ChatInputService getChatInputService() {
        return chatInputService;
    }
}
