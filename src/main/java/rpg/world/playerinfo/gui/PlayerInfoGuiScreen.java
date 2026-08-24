package rpg.world.playerinfo.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import rpg.api.GuiApi;
import rpg.api.JobApi;
import rpg.core.player.PlayerDataManager;
import rpg.extra.api.AchievementApi;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.quest.repository.QuestRepository;
import rpg.util.ItemBuilder;

/**
 * The nether-star "プレイヤー情報" root menu: evenly spaced category buttons
 * (クエスト・ジョブ・ステータス・スキル・実績), each opening its own dedicated sub-screen
 * (ステータス/スキルはorelia-coreの{@code /ol status}/{@code /ol skill}画面をそのまま開く)
 * instead of cramming every section into one inventory. Every sub-screen carries a "戻る"
 * button in its bottom-right slot that reopens this menu.
 *
 * <p>実績 opens orelia-extra's real achievement GUI directly via {@link AchievementApi}
 * (soft dependency - see {@code plugin.yml}) instead of relaying through the {@code /ol
 * achievement gui} command. The icon is omitted entirely when {@code achievementApi} is
 * {@code null} (OreliaExtra not installed), rather than showing a button that can't do anything.
 * Looked up fresh in {@link #build} rather than once at construction time - {@code
 * PlayerInfoModule} enables well before {@code ExtraApiModule} (which publishes it) in this
 * jar's single fixed module order, so a cached lookup from the constructor would always see
 * {@code null} even with achievements fully available; by the time a player actually opens
 * this menu, the whole plugin has long since finished enabling.
 */
public final class PlayerInfoGuiScreen {

    private static final int[] CATEGORY_SLOTS = {10, 12, 13, 14, 16};

    private final GuiManager guiManager;
    private final GuiApi guiApi;
    private final ServicesManager services;
    private final PlayerInfoQuestGuiScreen questScreen;
    private final PlayerInfoJobGuiScreen jobScreen;

    public PlayerInfoGuiScreen(QuestRepository questRepository, PlayerDataManager playerDataManager,
                                JobApi jobApi, GuiApi guiApi, ServicesManager services,
                                GuiManager guiManager) {
        this.guiManager = guiManager;
        this.guiApi = guiApi;
        this.services = services;
        this.questScreen = new PlayerInfoQuestGuiScreen(questRepository, playerDataManager);
        this.jobScreen = new PlayerInfoJobGuiScreen(jobApi);
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8プレイヤー情報", 27);
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
        return gui;
    }

    private GuiButton backButton(Player player) {
        return new GuiButton(new ItemBuilder(Material.ARROW).name("&%7戻る").build(),
                (p, clickType) -> guiManager.open(p, build(p)));
    }
}
