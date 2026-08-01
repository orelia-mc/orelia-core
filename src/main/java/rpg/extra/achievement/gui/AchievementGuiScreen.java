package rpg.extra.achievement.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.extra.achievement.model.AchievementDefinition;
import rpg.extra.achievement.service.AchievementService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * GUI counterpart of {@code /ol achievement}'s chat listing (SOW AchievementModule) - a
 * two-screen drill-down rather than one flat list, since {@link AchievementDefinition#getCategory()}
 * groups achievements and a chest GUI has no room to show every category's entries at once.
 * {@link #build} shows one button per distinct category (in {@code achievements.yml}'s own
 * order) with a done/total count; clicking one opens {@link #buildCategory}, a paginated list
 * of just that category's achievements. Both this and {@code rpg.dungeon.gui.DungeonGuiScreen}
 * (orelia-world) hit the same "orelia-core's {@code Gui} has no pagination primitive" gap and
 * independently grew the same small slot-paging helper - not shared, since the two repos don't
 * depend on each other.
 */
public final class AchievementGuiScreen {

    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int PAGE_SIZE = ITEM_SLOTS.length;
    private static final int PREV_PAGE_SLOT = 18;
    private static final int NEXT_PAGE_SLOT = 26;
    private static final int BACK_SLOT = 22;

    private final AchievementService achievementService;
    private final GuiManager guiManager;

    public AchievementGuiScreen(AchievementService achievementService, GuiManager guiManager) {
        this.achievementService = achievementService;
        this.guiManager = guiManager;
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8実績一覧", 27);
        Map<String, AchievementDefinition> all = achievementService.getAllAchievements();
        Set<String> unlocked = achievementService.getUnlocked(player.getUniqueId());

        List<String> categories = all.values().stream()
                .map(AchievementDefinition::getCategory)
                .distinct()
                .toList();

        // Not paginated - a server would need 17+ distinct achievement categories (not
        // individual achievements) before this screen itself needed paging, which
        // achievements.yml is nowhere near.
        int slot = 10;
        for (String category : categories) {
            if (slot > 16) {
                break;
            }
            List<AchievementDefinition> inCategory = all.values().stream()
                    .filter(achievement -> achievement.getCategory().equals(category))
                    .toList();
            long done = inCategory.stream().filter(achievement -> unlocked.contains(achievement.getId())).count();
            gui.set(slot++, new GuiButton(new ItemBuilder(Material.WRITTEN_BOOK)
                    .name("&%e" + category)
                    .lore(List.of("&%7達成状況: &%f" + done + "/" + inCategory.size(), "", "&%7クリックして一覧を表示"))
                    .build(), (clicker, clickType) -> guiManager.open(clicker, buildCategory(clicker, category, 0))));
        }
        return gui;
    }

    private Gui buildCategory(Player player, String category, int page) {
        Gui gui = new Gui("&%8実績 - " + category, 27);
        Map<String, AchievementDefinition> all = achievementService.getAllAchievements();
        Set<String> unlocked = achievementService.getUnlocked(player.getUniqueId());
        List<AchievementDefinition> items = all.values().stream()
                .filter(achievement -> achievement.getCategory().equals(category))
                .toList();

        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« ジャンル一覧に戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, build(clicker))));

        placePage(gui, items, page, achievement -> achievementButton(achievement, unlocked.contains(achievement.getId())),
                p -> buildCategory(player, category, p));
        return gui;
    }

    private GuiButton achievementButton(AchievementDefinition achievement, boolean done) {
        List<String> lore = new ArrayList<>();
        lore.add("&%7" + achievement.getDescription());
        lore.add("");
        lore.add(done ? "&%6達成済み" : "&%7未達成");
        if (achievement.getRewardSkillPoints() > 0) {
            lore.add("&%b報酬: &%fスキルポイント x" + achievement.getRewardSkillPoints());
        }
        return new GuiButton(new ItemBuilder(done ? Material.ENCHANTED_BOOK : Material.BOOK)
                .name((done ? "&%e" : "&%7") + achievement.getName())
                .lore(lore)
                .build(), (clicker, clickType) -> {
        });
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
}
