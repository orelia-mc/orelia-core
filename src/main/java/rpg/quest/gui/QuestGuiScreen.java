package rpg.quest.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.model.QuestData;
import rpg.quest.model.QuestState;
import rpg.quest.repository.QuestRepository;
import rpg.quest.service.QuestEligibilityService;
import rpg.quest.service.QuestProgressService;
import rpg.util.ItemBuilder;

import java.time.Duration;
import java.util.List;

/**
 * Quest-receptionist screen (SOW section 17 "クエスト"). Quest is a content-layer concern
 * (orelia-world), so this screen is built here rather than in orelia-core's gui module -
 * but it reuses orelia-core's generic {@code Gui}/{@code GuiButton} framework classes
 * directly (pure UI plumbing, not gameplay logic) instead of reinventing them. Orelia-core's
 * already-registered {@code GuiListener} handles clicks on any {@code Gui} it creates too,
 * so this module does not register a second click listener.
 */
public final class QuestGuiScreen {

    private final QuestRepository questRepository;
    private final QuestProgressService progressService;
    private final QuestEligibilityService eligibilityService;
    private final PlayerDataManager playerDataManager;
    private final MessageManager messages;

    public QuestGuiScreen(QuestRepository questRepository, QuestProgressService progressService,
                           QuestEligibilityService eligibilityService, PlayerDataManager playerDataManager,
                           MessageManager messages) {
        this.questRepository = questRepository;
        this.progressService = progressService;
        this.eligibilityService = eligibilityService;
        this.playerDataManager = playerDataManager;
        this.messages = messages;
    }

    public Gui build(Player player, List<String> offeredQuestIds) {
        Gui gui = new Gui("&%8クエスト", 27);
        PlayerQuestComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerQuestComponent.class))
                .orElse(null);

        int slot = 10;
        for (String questId : offeredQuestIds) {
            QuestData quest = questRepository.findById(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            QuestState state = component == null ? null
                    : component.getActiveQuests().containsKey(questId) ? component.getActiveQuests().get(questId).getState()
                    : component.hasCompleted(questId) ? QuestState.COMPLETE : QuestState.NOT_ACCEPTED;

            gui.set(slot++, new GuiButton(new ItemBuilder(Material.WRITABLE_BOOK)
                    .name("&%e" + quest.getName())
                    .lore(quest.getDescription())
                    .build(), (clicker, clickType) -> handleClick(clicker, quest, state)));
        }
        return gui;
    }

    private void handleClick(Player player, QuestData quest, QuestState state) {
        String questId = quest.getId();
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
