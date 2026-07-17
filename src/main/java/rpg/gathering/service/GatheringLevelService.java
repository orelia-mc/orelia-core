package rpg.gathering.service;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import rpg.core.player.PlayerDataManager;
import rpg.gathering.config.GatheringLevelingConfig;
import rpg.gathering.model.PlayerGatheringComponent;
import rpg.job.manager.JobManager;
import rpg.job.model.Job;
import rpg.job.service.JobService;

import java.util.UUID;

/**
 * Applies gathering/farming experience gains and level-ups shared by mining, woodcutting,
 * and farming (SOW 3.3).
 */
public final class GatheringLevelService {

    private static final String NO_JOB_LABEL = "無職";

    private final PlayerDataManager playerDataManager;
    private final GatheringLevelingConfig levelingConfig;
    private final JobService jobService;
    private final JobManager jobManager;

    public GatheringLevelService(PlayerDataManager playerDataManager, GatheringLevelingConfig levelingConfig,
                                  JobService jobService, JobManager jobManager) {
        this.playerDataManager = playerDataManager;
        this.levelingConfig = levelingConfig;
        this.jobService = jobService;
        this.jobManager = jobManager;
    }

    /** Adds experience to the player's gathering level, applying every level-up earned. */
    public void addExperience(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        playerDataManager.get(uuid).ifPresent(data -> {
            PlayerGatheringComponent component = data.require(PlayerGatheringComponent.class);
            int maxLevel = levelingConfig.getMaxLevel();
            if (component.getLevel() >= maxLevel) {
                return;
            }
            int previousLevel = component.getLevel();
            long experience = component.getExperience() + amount;
            int level = previousLevel;
            while (level < maxLevel && experience >= levelingConfig.requiredExperience(level)) {
                experience -= levelingConfig.requiredExperience(level);
                level++;
            }
            component.setLevel(level);
            component.setExperience(level >= maxLevel ? 0 : experience);
            if (level > previousLevel) {
                announceLevelUp(uuid, level);
            }
        });
    }

    /** Plays a level-up sound and notifies the player privately of their new gathering level. */
    private void announceLevelUp(UUID uuid, int newLevel) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        String jobName = jobService.getCurrentJob(uuid)
                .flatMap(jobManager::getDefinition)
                .map(Job::getDisplayName)
                .orElse(NO_JOB_LABEL);
        player.sendMessage(Component.text(player.getName() + ":" + jobName + "のレベルが" + newLevel + "に上がりました", NamedTextColor.GREEN));
    }

    public int getLevel(UUID uuid) {
        return playerDataManager.get(uuid)
                .flatMap(data -> data.component(PlayerGatheringComponent.class))
                .map(PlayerGatheringComponent::getLevel)
                .orElse(1);
    }
}
