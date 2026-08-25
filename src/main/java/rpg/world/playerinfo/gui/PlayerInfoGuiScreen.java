package rpg.world.playerinfo.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import rpg.api.GuiApi;
import rpg.api.JobApi;
import rpg.core.module.ModuleManager;
import rpg.core.player.PlayerDataManager;
import rpg.extra.api.AchievementApi;
import rpg.extra.chat.ChatModule;
import rpg.extra.friend.FriendModule;
import rpg.extra.guild.GuildModule;
import rpg.extra.guild.model.Guild;
import rpg.extra.party.PartyModule;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.quest.repository.QuestRepository;
import rpg.util.ItemBuilder;

/**
 * The nether-star "プレイヤー情報" root menu: evenly spaced category buttons on row 1
 * (クエスト・ジョブ・ステータス・スキル・実績), each opening its own dedicated sub-screen
 * (ステータス/スキルはorelia-coreの{@code /ol status}/{@code /ol skill}画面をそのまま開く),
 * plus a row 3 of social-feature shortcut buttons (ギルド・パーティー・フレンド・チャット) that
 * open each module's own top-level GUI screen directly. Every sub-screen carries a "戻る" button
 * in its bottom-right slot that reopens this menu.
 *
 * <p>実績 opens orelia-extra's real achievement GUI directly via {@link AchievementApi}
 * (soft dependency - see {@code plugin.yml}) instead of relaying through the {@code /ol
 * achievement gui} command. The icon is omitted entirely when {@code achievementApi} is
 * {@code null} (OreliaExtra not installed), rather than showing a button that can't do anything.
 *
 * <p>The social buttons (row 3) resolve {@link GuildModule}/{@link PartyModule}/
 * {@link FriendModule}/{@link ChatModule} via {@link ModuleManager#get} lazily inside each
 * button's click handler rather than once at construction time - {@code PlayerInfoModule}
 * enables well before any of those four modules in this jar's single fixed module order (content
 * layer registers before social/economy), so a lookup at construction would always resolve empty
 * even though every module is fully available by the time a player actually opens this menu.
 * Same lazy-lookup reasoning as {@code achievementApi} above, just via {@link ModuleManager}
 * instead of {@link ServicesManager} since these four are in-jar modules, not published
 * {@code *Api} facades.
 */
public final class PlayerInfoGuiScreen {

    private static final int[] CATEGORY_SLOTS = {10, 12, 13, 14, 16};
    private static final int[] SOCIAL_SLOTS = {19, 21, 23, 25};

    private final GuiManager guiManager;
    private final GuiApi guiApi;
    private final ServicesManager services;
    private final ModuleManager moduleManager;
    private final PlayerInfoQuestGuiScreen questScreen;
    private final PlayerInfoJobGuiScreen jobScreen;

    public PlayerInfoGuiScreen(QuestRepository questRepository, PlayerDataManager playerDataManager,
                                JobApi jobApi, GuiApi guiApi, ServicesManager services,
                                ModuleManager moduleManager, GuiManager guiManager) {
        this.guiManager = guiManager;
        this.guiApi = guiApi;
        this.services = services;
        this.moduleManager = moduleManager;
        this.questScreen = new PlayerInfoQuestGuiScreen(questRepository, playerDataManager);
        this.jobScreen = new PlayerInfoJobGuiScreen(jobApi);
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8プレイヤー情報", 45);
        gui.set(CATEGORY_SLOTS[0], new GuiButton(new ItemBuilder(Material.WRITABLE_BOOK).name("&%bクエスト").build(),
                (p, clickType) -> guiManager.open(p, questScreen.build(p, backButton(p)))));
        gui.set(CATEGORY_SLOTS[1], new GuiButton(new ItemBuilder(Material.LEATHER_HELMET).name("&%bジョブ").build(),
                (p, clickType) -> guiManager.open(p, jobScreen.build(p, backButton(p)))));
        gui.set(CATEGORY_SLOTS[2], new GuiButton(new ItemBuilder(Material.EXPERIENCE_BOTTLE).name("&%bステータス").build(),
                (p, clickType) -> guiApi.openStatus(p)));
        gui.set(CATEGORY_SLOTS[3], new GuiButton(new ItemBuilder(Material.ENCHANTED_BOOK).name("&%bスキル").build(),
                (p, clickType) -> guiApi.openSkill(p)));
        AchievementApi achievementApi = services.load(AchievementApi.class);
        if (achievementApi != null) {
            gui.set(CATEGORY_SLOTS[4], new GuiButton(new ItemBuilder(Material.NETHER_STAR).name("&%b実績").build(),
                    (p, clickType) -> {
                        p.closeInventory();
                        achievementApi.openGui(p);
                    }));
        }

        gui.set(SOCIAL_SLOTS[0], new GuiButton(new ItemBuilder(Material.WHITE_BANNER).name("&%aギルド").build(),
                (p, clickType) -> moduleManager.get(GuildModule.class).ifPresent(guildModule -> openGuild(guildModule, p))));
        gui.set(SOCIAL_SLOTS[1], new GuiButton(new ItemBuilder(Material.CYAN_BANNER).name("&%aパーティー").build(),
                (p, clickType) -> moduleManager.get(PartyModule.class).ifPresent(partyModule ->
                        partyModule.getGuiManager().open(p, partyModule.getPartyGuiScreen().build(p)))));
        gui.set(SOCIAL_SLOTS[2], new GuiButton(new ItemBuilder(Material.PLAYER_HEAD).name("&%aフレンド").build(),
                (p, clickType) -> moduleManager.get(FriendModule.class).ifPresent(friendModule ->
                        friendModule.getGuiManager().open(p, friendModule.getFriendGuiScreen().build(p)))));
        gui.set(SOCIAL_SLOTS[3], new GuiButton(new ItemBuilder(Material.WRITABLE_BOOK).name("&%aチャット設定").build(),
                (p, clickType) -> moduleManager.get(ChatModule.class).ifPresent(chatModule ->
                        chatModule.getGuiManager().open(p, chatModule.getChatGuiScreen().build(p)))));
        return gui;
    }

    /** Opens the viewer's own guild's detail screen if they're in one, otherwise the guild list - same fallback {@code GuildCommand}'s own no-argument/{@code gui} handling uses. */
    private void openGuild(GuildModule guildModule, Player player) {
        Guild guild = guildModule.getGuildService().getGuild(player.getUniqueId()).orElse(null);
        if (guild != null) {
            guildModule.getGuiManager().open(player, guildModule.getGuildGuiScreen().buildDetail(player, guild.getId()));
        } else {
            guildModule.getGuiManager().open(player, guildModule.getGuildGuiScreen().build(player));
        }
    }

    private GuiButton backButton(Player player) {
        return new GuiButton(new ItemBuilder(Material.ARROW).name("&%7戻る").build(),
                (p, clickType) -> guiManager.open(p, build(p)));
    }
}
