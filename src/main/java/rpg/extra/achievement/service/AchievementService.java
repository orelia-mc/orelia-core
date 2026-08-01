package rpg.extra.achievement.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import rpg.api.SkillApi;
import rpg.api.StatusApi;
import rpg.core.message.MessageManager;
import rpg.extra.achievement.model.AchievementDefinition;
import rpg.extra.achievement.repository.AchievementConfigRepository;
import rpg.extra.achievement.repository.AchievementProgressRepository;
import rpg.world.api.QuestApi;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically evaluates every online player against every {@link AchievementDefinition} and
 * grants rewards on first completion (SOW AchievementModule). {@link QuestApi} is optional -
 * orelia-world is only a soft dependency, so COMPLETE_QUEST conditions simply never trigger
 * if it isn't installed.
 */
public final class AchievementService {

    private final AchievementConfigRepository configRepository;
    private final AchievementProgressRepository progressRepository;
    private final StatusApi statusApi;
    private final SkillApi skillApi;
    private final Economy economy;
    private final QuestApi questApi;
    private final MessageManager messages;
    private final Map<UUID, Set<String>> unlockedByOwner = new ConcurrentHashMap<>();

    public AchievementService(AchievementConfigRepository configRepository, AchievementProgressRepository progressRepository,
                               StatusApi statusApi, SkillApi skillApi, Economy economy, QuestApi questApi,
                               MessageManager messages) {
        this.configRepository = configRepository;
        this.progressRepository = progressRepository;
        this.statusApi = statusApi;
        this.skillApi = skillApi;
        this.economy = economy;
        this.questApi = questApi;
        this.messages = messages;
    }

    public void loadAll() {
        unlockedByOwner.clear();
        unlockedByOwner.putAll(progressRepository.loadAll());
    }

    public Set<String> getUnlocked(UUID playerId) {
        return Set.copyOf(unlockedByOwner.getOrDefault(playerId, Set.of()));
    }

    public Map<String, AchievementDefinition> getAllAchievements() {
        return configRepository.getAll();
    }

    /** Checks every online player against every achievement and grants any newly-met ones. */
    public void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayer(player);
        }
    }

    public void checkPlayer(Player player) {
        Set<String> unlocked = unlockedByOwner.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        for (AchievementDefinition achievement : configRepository.getAll().values()) {
            if (unlocked.contains(achievement.getId())) {
                continue;
            }
            if (isMet(player, achievement)) {
                unlocked.add(achievement.getId());
                progressRepository.saveUnlock(player.getUniqueId(), achievement.getId());
                grantReward(player, achievement);
            }
        }
    }

    private boolean isMet(Player player, AchievementDefinition achievement) {
        return switch (achievement.getConditionType()) {
            case REACH_LEVEL -> statusApi.getLevel(player.getUniqueId())
                    .map(level -> level >= Integer.parseInt(achievement.getConditionValue()))
                    .orElse(false);
            case COMPLETE_QUEST -> questApi != null && questApi.hasCompletedQuest(player.getUniqueId(), achievement.getConditionValue());
            case MONEY_BALANCE -> economy != null && economy.getBalance(player) >= Double.parseDouble(achievement.getConditionValue());
        };
    }

    private void grantReward(Player player, AchievementDefinition achievement) {
        if (achievement.getRewardSkillPoints() > 0) {
            skillApi.grantSkillPoints(player.getUniqueId(), achievement.getRewardSkillPoints());
        }
        messages.send(player, "achievement.unlocked", "name", achievement.getName());
    }
}
