package rpg.quest.model;

import rpg.core.player.PlayerDataComponent;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player quest log: active quest progress, ever-completed quests (id -> last completed
 * timestamp, used for "one-time only" enforcement, prerequisite checks, and repeatable-quest
 * cooldowns), and earned titles.
 */
public final class PlayerQuestComponent implements PlayerDataComponent {

    private final UUID owner;
    private final Map<String, PlayerQuestProgress> activeQuests = new ConcurrentHashMap<>();
    private final Map<String, Instant> completedQuests;
    private final Set<String> titles;
    private String equippedTitle;

    public PlayerQuestComponent(UUID owner, Map<String, Instant> completedQuests, Set<String> titles, String equippedTitle) {
        this.owner = owner;
        this.completedQuests = new HashMap<>(completedQuests);
        this.titles = new HashSet<>(titles);
        this.equippedTitle = equippedTitle;
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public Map<String, PlayerQuestProgress> getActiveQuests() {
        return activeQuests;
    }

    public void startQuest(String questId) {
        activeQuests.put(questId, new PlayerQuestProgress(QuestState.IN_PROGRESS));
    }

    public void completeQuest(String questId) {
        activeQuests.remove(questId);
        completedQuests.put(questId, Instant.now());
    }

    public boolean hasCompleted(String questId) {
        return completedQuests.containsKey(questId);
    }

    public Optional<Instant> getLastCompletedAt(String questId) {
        return Optional.ofNullable(completedQuests.get(questId));
    }

    public Set<String> getCompletedQuestIds() {
        return Set.copyOf(completedQuests.keySet());
    }

    public Map<String, Instant> getCompletedQuestsWithTimestamps() {
        return Map.copyOf(completedQuests);
    }

    /** Debug helper: clears a quest's completion record, letting it be re-accepted immediately regardless of a repeatable quest's cooldown-hours. */
    public boolean clearCompletion(String questId) {
        return completedQuests.remove(questId) != null;
    }

    public void addTitle(String title) {
        if (title != null && !title.isBlank()) {
            titles.add(title);
        }
    }

    public Set<String> getTitles() {
        return Set.copyOf(titles);
    }

    /** The title currently shown in chat/tab-list (see orelia-serverutil's {@code {title}} placeholder), or {@code null} if none is equipped. */
    public String getEquippedTitle() {
        return equippedTitle;
    }

    /** {@code null} unequips. Does not validate that {@code title} was actually earned - callers (e.g. {@code TitleCommand}) check {@link #getTitles()} first. */
    public void setEquippedTitle(String title) {
        this.equippedTitle = title;
    }
}
