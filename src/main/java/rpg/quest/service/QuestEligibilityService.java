package rpg.quest.service;

import org.bukkit.entity.Player;
import rpg.api.StatusApi;
import rpg.core.player.PlayerDataManager;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.model.QuestData;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Evaluates the "クエストフラグ" rules from SOW section 11: prerequisite quests, one-time
 * vs repeatable, required level, time-of-day availability, and (for a repeatable quest with
 * a configured cooldown) whether enough time has passed since it was last completed.
 */
public final class QuestEligibilityService {

    public enum Ineligibility {
        ALREADY_ACTIVE, ALREADY_COMPLETED, LEVEL_TOO_LOW, PREREQUISITE_MISSING, NOT_AVAILABLE_NOW, ON_COOLDOWN
    }

    private final PlayerDataManager playerDataManager;
    private final StatusApi statusApi;

    public QuestEligibilityService(PlayerDataManager playerDataManager, StatusApi statusApi) {
        this.playerDataManager = playerDataManager;
        this.statusApi = statusApi;
    }

    public Optional<Ineligibility> checkEligibility(Player player, QuestData quest) {
        PlayerQuestComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerQuestComponent.class))
                .orElse(null);
        if (component == null) {
            return Optional.of(Ineligibility.LEVEL_TOO_LOW);
        }
        if (component.getActiveQuests().containsKey(quest.getId())) {
            return Optional.of(Ineligibility.ALREADY_ACTIVE);
        }
        if (component.hasCompleted(quest.getId()) && !quest.isRepeatable()) {
            return Optional.of(Ineligibility.ALREADY_COMPLETED);
        }
        if (quest.isRepeatable() && quest.getCooldownHours() > 0 && remainingCooldown(component, quest).isPresent()) {
            return Optional.of(Ineligibility.ON_COOLDOWN);
        }
        int level = statusApi.getLevel(player.getUniqueId()).orElse(1);
        if (level < quest.getRequiredLevel()) {
            return Optional.of(Ineligibility.LEVEL_TOO_LOW);
        }
        for (String prerequisite : quest.getPrerequisiteQuestIds()) {
            if (!component.hasCompleted(prerequisite)) {
                return Optional.of(Ineligibility.PREREQUISITE_MISSING);
            }
        }
        if (!quest.isAvailableAtHour(LocalDateTime.now().getHour())) {
            return Optional.of(Ineligibility.NOT_AVAILABLE_NOW);
        }
        return Optional.empty();
    }

    /** How much longer until a repeatable-with-cooldown quest can be re-accepted, or empty if it's already off cooldown (or was never completed). */
    public Optional<Duration> remainingCooldown(Player player, QuestData quest) {
        PlayerQuestComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerQuestComponent.class))
                .orElse(null);
        return component == null ? Optional.empty() : remainingCooldown(component, quest);
    }

    private Optional<Duration> remainingCooldown(PlayerQuestComponent component, QuestData quest) {
        Instant last = component.getLastCompletedAt(quest.getId()).orElse(null);
        if (last == null) {
            return Optional.empty();
        }
        Instant readyAt = last.plus(Duration.ofMinutes(Math.round(quest.getCooldownHours() * 60)));
        Duration remaining = Duration.between(Instant.now(), readyAt);
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }
}
