package rpg.quest.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.gui.framework.GuiPageLayout;
import rpg.gui.framework.GuiPaginator;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.model.PlayerQuestProgress;
import rpg.quest.model.QuestData;
import rpg.quest.model.QuestObjective;
import rpg.quest.model.QuestState;
import rpg.quest.model.QuestType;
import rpg.quest.repository.QuestRepository;
import rpg.quest.service.QuestEligibilityService;
import rpg.quest.service.QuestProgressService;
import rpg.util.ItemBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Quest GUI (SOW section 17 "クエスト"). Two entry points:
 *
 * <ul>
 *   <li>{@link #build(Player, List)} - a specific NPC's curated offer list (unchanged call
 *   shape, still used by {@code NpcInteractListener}'s {@code QUEST_RECEPTIONIST} case).</li>
 *   <li>{@link #build(Player)} - the player's full quest log across every quest in
 *   {@code quests.yml}, grouped by {@link QuestType} (a category-list -> paginated-quest-list
 *   drill-down, same shape as orelia-extra's {@code AchievementGuiScreen}). This is what
 *   {@code /ol quest gui} and {@code WorldDebugApi#openQuest} open - there was previously no
 *   command path to this screen at all, only the NPC-offer one.</li>
 * </ul>
 *
 * <p>Both paginate through orelia-core's shared {@link GuiPaginator}/{@link GuiPageLayout}
 * rather than a fixed 7-slot list with no overflow handling (the previous version of this
 * class silently ran off the edge of the inventory past 7 offered quests). A not-currently-
 * eligible quest is still shown (locked, with the reason in its lore) rather than hidden -
 * NPCs' offer lists and the full log both no longer need to pre-filter for eligibility
 * themselves before handing quest ids to this screen.
 */
public final class QuestGuiScreen {

    private static final GuiPageLayout LAYOUT =
            new GuiPageLayout(new int[]{10, 11, 12, 13, 14, 15, 16}, 18, 26);
    private static final int BAR_LENGTH = 10;
    private static final int BACK_SLOT = 22;

    private final QuestRepository questRepository;
    private final QuestProgressService progressService;
    private final QuestEligibilityService eligibilityService;
    private final PlayerDataManager playerDataManager;
    private final MessageManager messages;
    private final GuiManager guiManager;
    private final QuestObjectiveBarRenderer barRenderer = new QuestObjectiveBarRenderer();

    public QuestGuiScreen(QuestRepository questRepository, QuestProgressService progressService,
                           QuestEligibilityService eligibilityService, PlayerDataManager playerDataManager,
                           MessageManager messages, GuiManager guiManager) {
        this.questRepository = questRepository;
        this.progressService = progressService;
        this.eligibilityService = eligibilityService;
        this.playerDataManager = playerDataManager;
        this.messages = messages;
        this.guiManager = guiManager;
    }

    /** An NPC's curated offer list - id order as configured on the NPC, not grouped by type. */
    public Gui build(Player player, List<String> offeredQuestIds) {
        return build(player, offeredQuestIds, 0);
    }

    private Gui build(Player player, List<String> offeredQuestIds, int page) {
        Gui gui = new Gui("&%8クエスト", 27);
        List<QuestData> quests = offeredQuestIds.stream()
                .map(id -> questRepository.findById(id).orElse(null))
                .filter(quest -> quest != null)
                .toList();
        PlayerQuestComponent component = questComponent(player);
        GuiPaginator.placePage(guiManager, gui, LAYOUT, quests, page,
                quest -> questButton(player, quest, component), p -> build(player, offeredQuestIds, p));
        return gui;
    }

    /** Full quest log across every quest in {@code quests.yml}, grouped by {@link QuestType}. */
    public Gui build(Player player) {
        return buildTypeList(player, 0);
    }

    private Gui buildTypeList(Player player, int page) {
        Gui gui = new Gui("&%8クエストログ", 27);
        List<QuestType> types = questRepository.getAll().values().stream()
                .map(QuestData::getType).distinct().toList();
        GuiPaginator.placePage(guiManager, gui, LAYOUT, types, page,
                type -> typeButton(player, type), p -> buildTypeList(player, p));
        return gui;
    }

    private GuiButton typeButton(Player player, QuestType type) {
        List<QuestData> inType = questRepository.getAll().values().stream()
                .filter(quest -> quest.getType() == type).toList();
        return new GuiButton(new ItemBuilder(Material.MAP)
                .name("&%e" + type)
                .lore(List.of("&%7" + inType.size() + "件のクエスト", "", "&%7クリックして一覧を表示"))
                .build(), (clicker, clickType) -> guiManager.open(clicker, buildTypeQuestList(player, type, 0)));
    }

    private Gui buildTypeQuestList(Player player, QuestType type, int page) {
        Gui gui = new Gui("&%8クエスト - " + type, 27);
        gui.set(BACK_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%c« カテゴリ一覧に戻る").build(),
                (clicker, clickType) -> guiManager.open(clicker, buildTypeList(player, 0))));

        List<QuestData> quests = questRepository.getAll().values().stream()
                .filter(quest -> quest.getType() == type).toList();
        PlayerQuestComponent component = questComponent(player);
        GuiPaginator.placePage(guiManager, gui, LAYOUT, quests, page,
                quest -> questButton(player, quest, component), p -> buildTypeQuestList(player, type, p));
        return gui;
    }

    private PlayerQuestComponent questComponent(Player player) {
        return playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerQuestComponent.class))
                .orElse(null);
    }

    private GuiButton questButton(Player player, QuestData quest, PlayerQuestComponent component) {
        QuestState state = state(component, quest.getId());
        List<String> lore = new ArrayList<>(quest.getDescription());
        lore.add("");

        Material material;
        boolean locked = false;
        if (state == QuestState.IN_PROGRESS || state == QuestState.AWAITING_REPORT) {
            appendObjectiveLore(lore, quest, component.getActiveQuests().get(quest.getId()));
            lore.add(state == QuestState.AWAITING_REPORT ? "&%6報告可能 - クリックして報告" : "&%7進行中");
            material = Material.WRITTEN_BOOK;
        } else if (state == QuestState.COMPLETE) {
            lore.add("&%6達成済み");
            material = Material.ENCHANTED_BOOK;
        } else {
            var failure = eligibilityService.checkEligibility(player, quest);
            if (failure.isPresent()) {
                locked = true;
                lore.add(lockReasonLore(player, quest, failure.get()));
                material = Material.BARRIER;
            } else {
                lore.add("&%aクリックして受注");
                material = Material.WRITABLE_BOOK;
            }
        }

        boolean finalLocked = locked;
        return new GuiButton(new ItemBuilder(material)
                .name((finalLocked ? "&%8" : "&%e") + quest.getName())
                .lore(lore)
                .build(), (clicker, clickType) -> {
            if (!finalLocked) {
                handleClick(clicker, quest, state, clickType);
            }
        });
    }

    private QuestState state(PlayerQuestComponent component, String questId) {
        if (component == null) {
            return null;
        }
        PlayerQuestProgress progress = component.getActiveQuests().get(questId);
        if (progress != null) {
            return progress.getState();
        }
        return component.hasCompleted(questId) ? QuestState.COMPLETE : QuestState.NOT_ACCEPTED;
    }

    private void appendObjectiveLore(List<String> lore, QuestData quest, PlayerQuestProgress progress) {
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            int current = Math.min(progress.getProgress(i), objective.getRequiredAmount());
            String bar = barRenderer.render(current, objective.getRequiredAmount(), BAR_LENGTH, "&%a", "&%8");
            lore.add("&%7" + objective.getTargetId() + " " + bar + " &%f" + current + "/" + objective.getRequiredAmount());
        }
    }

    private String lockReasonLore(Player player, QuestData quest, QuestEligibilityService.Ineligibility reason) {
        if (reason == QuestEligibilityService.Ineligibility.ON_COOLDOWN) {
            Duration remaining = eligibilityService.remainingCooldown(player, quest).orElse(Duration.ZERO);
            return messages.format("quest.locked-reason.on-cooldown-timed", "hours", remaining.toHours(), "minutes", remaining.toMinutesPart());
        }
        String key = switch (reason) {
            case ALREADY_ACTIVE -> "quest.locked-reason.already-active";
            case ALREADY_COMPLETED -> "quest.locked-reason.already-completed";
            case LEVEL_TOO_LOW -> "quest.locked-reason.level-too-low";
            case PREREQUISITE_MISSING -> "quest.locked-reason.prerequisite-missing";
            case NOT_AVAILABLE_NOW -> "quest.locked-reason.not-available-now";
            case NOT_IN_PARTY -> "quest.locked-reason.not-in-party";
            case ON_COOLDOWN -> "quest.locked-reason.on-cooldown-timed"; // unreachable, handled above
        };
        return messages.format(key);
    }

    /** Shift-click abandons an in-progress (or awaiting-report) quest; a plain click accepts/reports as before. */
    private void handleClick(Player player, QuestData quest, QuestState state, String clickType) {
        String questId = quest.getId();
        if (clickType != null && clickType.startsWith("SHIFT_")
                && (state == QuestState.IN_PROGRESS || state == QuestState.AWAITING_REPORT)) {
            boolean abandoned = progressService.abandon(player.getUniqueId(), questId);
            messages.send(player, abandoned ? "quest.abandoned" : "quest.not-active", "quest", questId);
            return;
        }
        if (state == QuestState.AWAITING_REPORT) {
            boolean reported = progressService.report(player, questId);
            messages.send(player, reported ? "quest.completed" : "quest.report-failed");
        } else if (state == null || state == QuestState.NOT_ACCEPTED) {
            var failure = progressService.accept(player, questId);
            if (failure.isEmpty()) {
                messages.send(player, "quest.accepted");
            } else if (failure.get() == QuestEligibilityService.Ineligibility.ON_COOLDOWN) {
                Duration remaining = eligibilityService.remainingCooldown(player, quest).orElse(Duration.ZERO);
                messages.send(player, "quest.on-cooldown", "hours", remaining.toHours(), "minutes", remaining.toMinutesPart());
            } else {
                messages.send(player, "quest.requirements-not-met", "reason", failure.get());
            }
        } else {
            messages.send(player, "quest.in-progress");
        }
    }
}
