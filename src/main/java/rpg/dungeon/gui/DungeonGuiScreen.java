package rpg.dungeon.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.api.StatusApi;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.dungeon.model.DungeonData;
import rpg.dungeon.model.PlayerDungeonComponent;
import rpg.dungeon.repository.DungeonRepository;
import rpg.dungeon.service.DungeonEncounterService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.util.ItemBuilder;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Lists the viewing player's unlocked dungeons; clicking one opens {@link #buildDifficultySelect}
 * instead of starting the run directly, same as right-clicking its trigger block a second time
 * ({@code DungeonBlockInteractListener}). Same shape as {@code QuestGuiScreen} - plain
 * {@code build(Player) -> Gui} methods reusing orelia-core's generic Gui framework.
 *
 * <p>Both listings page through {@link #ITEM_SLOTS} (row 2's 7 interior cells) instead of
 * spilling past slot 16 into the border/next-row cells with no gap - orelia-core's {@code Gui}
 * has no built-in pagination concept, so {@link #placePage} is a small paginator built directly
 * on {@code GuiButton}/{@code Gui.set}, reused by both {@link #build} (unlocked dungeons) and
 * {@link #buildDifficultySelect} (a dungeon's difficulty tiers) since both can exceed 7 entries -
 * up to 12 tiers in {@link DungeonEncounterService#DIFFICULTY_TIERS} alone.
 */
public final class DungeonGuiScreen {

    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PAGE_SIZE = ITEM_SLOTS.length;
    private static final int PREV_PAGE_SLOT = 18;
    private static final int NEXT_PAGE_SLOT = 26;

    private final DungeonRepository repository;
    private final DungeonEncounterService encounterService;
    private final PlayerDataManager playerDataManager;
    private final StatusApi statusApi;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public DungeonGuiScreen(DungeonRepository repository, DungeonEncounterService encounterService,
                             PlayerDataManager playerDataManager, StatusApi statusApi, GuiManager guiManager,
                             MessageManager messages) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.playerDataManager = playerDataManager;
        this.statusApi = statusApi;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    public Gui build(Player player) {
        return build(player, 0);
    }

    private Gui build(Player player, int page) {
        Gui gui = new Gui("&%8ダンジョン", 27);
        PlayerDungeonComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerDungeonComponent.class))
                .orElse(null);
        Set<String> unlockedIds = component == null ? Set.of() : component.getUnlockedDungeonIds();
        // Order by repository.getAll()'s dungeons.yml order (a stable LinkedHashMap), not the
        // unlocked-id Set's own iteration order, so a dungeon doesn't appear to jump pages
        // between two page-navigation clicks.
        List<DungeonData> unlocked = repository.getAll().values().stream()
                .filter(data -> unlockedIds.contains(data.getId()))
                .toList();

        placePage(gui, unlocked, page, data -> new GuiButton(new ItemBuilder(Material.NETHER_STAR)
                .name("&%e" + data.getName())
                .lore(List.of(
                        "&%7クリックして難易度を選択",
                        "&%7人数: " + data.getMinPartySize() + "〜" + data.getMaxPartySize(),
                        "&%7制限時間: " + data.getTimeLimitSeconds() + "秒"))
                .build(), (clicker, clickType) -> guiManager.open(clicker, buildDifficultySelect(clicker, data.getId()))),
                p -> build(player, p));
        return gui;
    }

    /**
     * Fixed difficulty tiers ({@link DungeonEncounterService#DIFFICULTY_TIERS}) up to the
     * viewer's own character level - if none qualify (very low level), falls back to a single
     * "通常" (unscaled) button instead of leaving the screen empty.
     */
    public Gui buildDifficultySelect(Player player, String dungeonId) {
        return buildDifficultySelect(player, dungeonId, 0);
    }

    private Gui buildDifficultySelect(Player player, String dungeonId, int page) {
        Gui gui = new Gui("&%8難易度選択", 27);
        int level = statusApi.getLevel(player.getUniqueId()).orElse(1);
        List<Integer> eligible = DungeonEncounterService.DIFFICULTY_TIERS.stream().filter(tier -> tier <= level).toList();

        if (eligible.isEmpty()) {
            gui.set(13, new GuiButton(new ItemBuilder(Material.NETHER_STAR)
                    .name("&%e通常")
                    .lore(List.of("&%7クリックして挑戦", "&%8推奨レベルに届いていないため難易度選択は利用できません。"))
                    .build(), (clicker, clickType) -> handleDifficultyClick(clicker, dungeonId, null)));
            return gui;
        }
        placePage(gui, eligible, page, tier -> new GuiButton(new ItemBuilder(Material.NETHER_STAR)
                .name("&%eLv. " + tier)
                .lore(List.of("&%7クリックしてこの難易度で挑戦"))
                .build(), (clicker, clickType) -> handleDifficultyClick(clicker, dungeonId, tier)),
                p -> buildDifficultySelect(player, dungeonId, p));
        return gui;
    }

    /** Places up to {@link #PAGE_SIZE} items of {@code page} into {@link #ITEM_SLOTS}, adding prev/next-page buttons only where another page actually exists. */
    private <T> void placePage(Gui gui, List<T> items, int page, Function<T, GuiButton> toButton, IntFunction<Gui> pageBuilder) {
        int start = page * PAGE_SIZE;
        List<T> pageItems = items.subList(Math.min(start, items.size()), Math.min(start + PAGE_SIZE, items.size()));
        for (int i = 0; i < pageItems.size(); i++) {
            gui.set(ITEM_SLOTS[i], toButton.apply(pageItems.get(i)));
        }
        if (page > 0) {
            gui.set(PREV_PAGE_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%a« 前のページ").build(),
                    (clicker, clickType) -> guiManager.open(clicker, pageBuilder.apply(page - 1))));
        }
        if (start + PAGE_SIZE < items.size()) {
            gui.set(NEXT_PAGE_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%a次のページ »").build(),
                    (clicker, clickType) -> guiManager.open(clicker, pageBuilder.apply(page + 1))));
        }
    }

    private void handleDifficultyClick(Player player, String dungeonId, Integer difficulty) {
        player.closeInventory();
        encounterService.challenge(player, dungeonId, difficulty)
                .ifPresent(failure -> messages.send(player, "dungeon.challenge-failed." + failure.name().toLowerCase()));
    }
}
