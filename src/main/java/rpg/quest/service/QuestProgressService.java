package rpg.quest.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.quest.model.ObjectiveType;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.model.PlayerQuestProgress;
import rpg.quest.model.QuestData;
import rpg.quest.model.QuestObjective;
import rpg.quest.model.QuestState;
import rpg.quest.repository.QuestRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Drives one quest through 未受注 → 受注中 → 達成 → 報告待ち → 完了 (SOW section 11):
 * accepting, recording objective progress from gameplay events, and reporting completion
 * to a quest NPC for rewards.
 */
public final class QuestProgressService {

    private final PlayerDataManager playerDataManager;
    private final QuestRepository questRepository;
    private final QuestEligibilityService eligibilityService;
    private final QuestRewardService rewardService;
    private final QuestItemInventoryService inventoryService;
    private final MessageManager messages;

    public QuestProgressService(PlayerDataManager playerDataManager, QuestRepository questRepository,
                                 QuestEligibilityService eligibilityService, QuestRewardService rewardService,
                                 QuestItemInventoryService inventoryService, MessageManager messages) {
        this.playerDataManager = playerDataManager;
        this.questRepository = questRepository;
        this.eligibilityService = eligibilityService;
        this.rewardService = rewardService;
        this.inventoryService = inventoryService;
        this.messages = messages;
    }

    public Optional<QuestEligibilityService.Ineligibility> accept(Player player, String questId) {
        QuestData quest = questRepository.findById(questId).orElse(null);
        if (quest == null) {
            return Optional.of(QuestEligibilityService.Ineligibility.PREREQUISITE_MISSING);
        }
        Optional<QuestEligibilityService.Ineligibility> ineligible = eligibilityService.checkEligibility(player, quest);
        if (ineligible.isPresent()) {
            return ineligible;
        }
        component(player.getUniqueId()).ifPresent(c -> c.startQuest(questId));
        return Optional.empty();
    }

    /**
     * Reports a finished quest to the NPC and grants rewards. Returns false if the quest
     * is not yet in {@link QuestState#AWAITING_REPORT}.
     */
    public boolean report(Player player, String questId) {
        PlayerQuestComponent component = component(player.getUniqueId()).orElse(null);
        QuestData quest = questRepository.findById(questId).orElse(null);
        if (component == null || quest == null) {
            return false;
        }
        PlayerQuestProgress progress = component.getActiveQuests().get(questId);
        if (progress == null || progress.getState() != QuestState.AWAITING_REPORT) {
            return false;
        }
        for (QuestObjective objective : quest.getObjectives()) {
            if (objective.getType() == ObjectiveType.DELIVER_ITEM
                    && !inventoryService.consume(player, objective, objective.getRequiredAmount())) {
                return false;
            }
        }
        component.completeQuest(questId);
        rewardService.grant(player, quest.getReward());
        notifyNewlyUnlockedQuests(player, questId);
        return true;
    }

    /** Tells the player about every quest that just became acceptable now that {@code completedQuestId} is done (SOW section 11 prerequisite chains). */
    private void notifyNewlyUnlockedQuests(Player player, String completedQuestId) {
        for (QuestData candidate : questRepository.getAll().values()) {
            if (candidate.getPrerequisiteQuestIds().contains(completedQuestId)
                    && eligibilityService.checkEligibility(player, candidate).isEmpty()) {
                messages.send(player, "quest.newly-unlocked", "quest", candidate.getName());
            }
        }
    }

    /**
     * Re-checks {@link ObjectiveType#COLLECT_ITEM}, {@code DELIVER_ITEM} (possession only
     * - actual consumption happens at {@link #report}) and {@link ObjectiveType#REACH_LOCATION}
     * objectives, which have no single triggering Bukkit event. Called periodically by
     * {@link rpg.quest.QuestModule} for every online player.
     */
    public void checkPeriodicObjectives(Player player) {
        forEachInProgressQuest(player.getUniqueId(), (quest, progress) -> {
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective.getType() == ObjectiveType.COLLECT_ITEM || objective.getType() == ObjectiveType.DELIVER_ITEM) {
                    int count = inventoryService.countMatching(player, objective);
                    progress.setProgress(i, Math.min(objective.getRequiredAmount(), count));
                } else if (objective.getType() == ObjectiveType.REACH_LOCATION) {
                    if (isWithinRadius(player, objective)) {
                        progress.setProgress(i, objective.getRequiredAmount());
                    }
                }
            }
        });
    }

    private boolean isWithinRadius(Player player, QuestObjective objective) {
        Location location = player.getLocation();
        if (location.getWorld() == null || !location.getWorld().getName().equals(objective.getWorld())) {
            return false;
        }
        double dx = location.getX() - objective.getX();
        double dy = location.getY() - objective.getY();
        double dz = location.getZ() - objective.getZ();
        return (dx * dx + dy * dy + dz * dz) <= objective.getRadius() * objective.getRadius();
    }

    public void onMonsterKilled(UUID playerId, String monsterId) {
        progressMatchingObjectives(playerId, ObjectiveType.KILL_MONSTER, monsterId, 1);
    }

    public void onBossKilled(UUID playerId, String bossId) {
        progressMatchingObjectives(playerId, ObjectiveType.KILL_BOSS, bossId, 1);
    }

    public void onNpcTalked(UUID playerId, String npcId) {
        progressMatchingObjectives(playerId, ObjectiveType.TALK_NPC, npcId, 1);
    }

    /** Hook for the dungeon module to call once integrated; not yet wired automatically. */
    public void onDungeonCleared(UUID playerId, String dungeonId) {
        progressMatchingObjectives(playerId, ObjectiveType.CLEAR_DUNGEON, dungeonId, 1);
    }

    private void progressMatchingObjectives(UUID playerId, ObjectiveType type, String targetId, int increment) {
        forEachInProgressQuest(playerId, (quest, progress) -> {
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective.getType() == type && targetId.equals(objective.getTargetId())) {
                    int current = progress.getProgress(i);
                    progress.setProgress(i, Math.min(objective.getRequiredAmount(), current + increment));
                }
            }
        });
    }

    /**
     * Runs {@code action} over every quest the player currently has {@link QuestState#IN_PROGRESS},
     * then re-evaluates whether that quest just became complete.
     */
    private void forEachInProgressQuest(UUID playerId, BiConsumer<QuestData, PlayerQuestProgress> action) {
        component(playerId).ifPresent(component -> {
            for (var entry : component.getActiveQuests().entrySet()) {
                QuestData quest = questRepository.findById(entry.getKey()).orElse(null);
                if (quest == null || entry.getValue().getState() != QuestState.IN_PROGRESS) {
                    continue;
                }
                action.accept(quest, entry.getValue());
                evaluateCompletion(quest, entry.getValue());
            }
        });
    }

    private void evaluateCompletion(QuestData quest, PlayerQuestProgress progress) {
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (progress.getProgress(i) < objectives.get(i).getRequiredAmount()) {
                return;
            }
        }
        progress.setState(QuestState.AWAITING_REPORT);
    }

    /**
     * Debug helper: force every objective of an in-progress quest straight to completion
     * (still requires a normal {@link #report} afterwards for reward grant). Used by
     * orelia-debug's testplay tooling to skip a quest's objectives without playing them out.
     */
    public boolean forceCompleteObjectives(UUID playerId, String questId) {
        PlayerQuestComponent component = component(playerId).orElse(null);
        QuestData quest = questRepository.findById(questId).orElse(null);
        if (component == null || quest == null) {
            return false;
        }
        PlayerQuestProgress progress = component.getActiveQuests().get(questId);
        if (progress == null || progress.getState() != QuestState.IN_PROGRESS) {
            return false;
        }
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            progress.setProgress(i, objectives.get(i).getRequiredAmount());
        }
        evaluateCompletion(quest, progress);
        return true;
    }

    /**
     * Debug helper: force-starts a quest for a player, bypassing the prerequisite/level
     * eligibility checks {@link #accept} normally enforces. No-op (returns false) if the
     * quest doesn't exist or is already active for that player.
     */
    public boolean forceStartQuest(UUID playerId, String questId) {
        PlayerQuestComponent component = component(playerId).orElse(null);
        QuestData quest = questRepository.findById(questId).orElse(null);
        if (component == null || quest == null || component.getActiveQuests().containsKey(questId)) {
            return false;
        }
        component.startQuest(questId);
        return true;
    }

    /**
     * Debug helper: clears a quest's completion record, letting a repeatable quest be
     * re-accepted immediately regardless of its {@code cooldown-hours}.
     */
    public boolean resetCompletion(UUID playerId, String questId) {
        return component(playerId).map(c -> c.clearCompletion(questId)).orElse(false);
    }

    /** Debug helper: grants a title without requiring the quest reward that normally awards it. */
    public boolean grantTitle(UUID playerId, String title) {
        PlayerQuestComponent component = component(playerId).orElse(null);
        if (component == null) {
            return false;
        }
        component.addTitle(title);
        return true;
    }

    /**
     * Debug helper: force-equips a title, bypassing the "must already be earned" check
     * {@link rpg.world.api.QuestApi#equipTitle} enforces - useful to preview a title's
     * display before deciding to wire it into a quest reward.
     */
    public boolean forceEquipTitle(UUID playerId, String title) {
        PlayerQuestComponent component = component(playerId).orElse(null);
        if (component == null) {
            return false;
        }
        component.setEquippedTitle(title);
        return true;
    }

    public boolean unequipTitle(UUID playerId) {
        PlayerQuestComponent component = component(playerId).orElse(null);
        if (component == null) {
            return false;
        }
        component.setEquippedTitle(null);
        return true;
    }

    /** Every quest id defined in {@code quests.yml}, for admin visibility/tab-completion. */
    public List<String> listQuestIds() {
        return List.copyOf(questRepository.getAll().keySet());
    }

    private Optional<PlayerQuestComponent> component(UUID uuid) {
        return playerDataManager.get(uuid).flatMap(d -> d.component(PlayerQuestComponent.class));
    }
}
