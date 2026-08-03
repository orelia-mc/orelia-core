package rpg.world.playerinfo.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.player.PlayerDataManager;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.model.PlayerQuestProgress;
import rpg.quest.model.QuestData;
import rpg.quest.model.QuestObjective;
import rpg.quest.model.QuestState;
import rpg.quest.repository.QuestRepository;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dedicated "クエスト" sub-screen of the player-info nether-star menu: a read-only list of
 * the player's currently accepted quests. Opened from {@link PlayerInfoGuiScreen}, which
 * supplies the back button placed in this screen's bottom-right slot.
 */
public final class PlayerInfoQuestGuiScreen {

    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int SIZE = 36;
    private static final int BACK_SLOT = SIZE - 1;

    private final QuestRepository questRepository;
    private final PlayerDataManager playerDataManager;

    public PlayerInfoQuestGuiScreen(QuestRepository questRepository, PlayerDataManager playerDataManager) {
        this.questRepository = questRepository;
        this.playerDataManager = playerDataManager;
    }

    public Gui build(Player player, GuiButton backButton) {
        Gui gui = new Gui("&%8プレイヤー情報 - クエスト", SIZE);
        gui.set(BACK_SLOT, backButton);

        PlayerQuestComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerQuestComponent.class))
                .orElse(null);
        Map<String, PlayerQuestProgress> activeQuests = component == null ? Map.of() : component.getActiveQuests();

        if (activeQuests.isEmpty()) {
            gui.set(QUEST_SLOTS[0], GuiButton.display(new ItemBuilder(Material.PAPER)
                    .name("&%7受注中のクエストはありません")
                    .build()));
            return gui;
        }

        int slotIndex = 0;
        for (var entry : activeQuests.entrySet()) {
            if (slotIndex >= QUEST_SLOTS.length) {
                break;
            }
            QuestData quest = questRepository.findById(entry.getKey()).orElse(null);
            if (quest == null) {
                continue;
            }
            gui.set(QUEST_SLOTS[slotIndex++], GuiButton.display(new ItemBuilder(Material.WRITTEN_BOOK)
                    .name("&%e" + quest.getName())
                    .lore(questLore(quest, entry.getValue()))
                    .build()));
        }
        return gui;
    }

    private List<String> questLore(QuestData quest, PlayerQuestProgress progress) {
        List<String> lore = new ArrayList<>(quest.getDescription());
        lore.add("");
        lore.add("&%7状態: " + stateLabel(progress.getState()));
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            lore.add("&%7" + objectiveLabel(objective) + ": " + progress.getProgress(i) + "/" + objective.getRequiredAmount());
        }
        return lore;
    }

    private String stateLabel(QuestState state) {
        return switch (state) {
            case IN_PROGRESS -> "&%f受注中";
            case ACHIEVED, AWAITING_REPORT -> "&%a報告待ち";
            case COMPLETE -> "&%a完了";
            case NOT_ACCEPTED -> "&%7未受注";
        };
    }

    private String objectiveLabel(QuestObjective objective) {
        String target = objective.getTargetId() == null ? "" : " (" + objective.getTargetId() + ")";
        return switch (objective.getType()) {
            case KILL_MONSTER -> "モンスター討伐" + target;
            case KILL_BOSS -> "ボス討伐" + target;
            case COLLECT_ITEM -> "アイテム収集" + target;
            case DELIVER_ITEM -> "アイテム納品" + target;
            case REACH_LOCATION -> "目的地への到達";
            case TALK_NPC -> "NPCとの会話" + target;
            case CLEAR_DUNGEON -> "ダンジョン攻略" + target;
        };
    }
}
