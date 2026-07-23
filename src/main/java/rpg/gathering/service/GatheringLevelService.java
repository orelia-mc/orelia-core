package rpg.gathering.service;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import rpg.core.player.PlayerDataManager;
import rpg.gathering.config.GatheringLevelingConfig;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.model.PlayerGatheringComponent;
import rpg.job.manager.JobManager;
import rpg.job.model.Job;

import java.util.UUID;

/**
 * Applies gathering/farming experience gains and level-ups, tracked independently per
 * {@link GatherActionType} (SOW 3.3 update: mining levels the miner job, woodcutting the
 * woodcutter job, farming the farmer job - no longer one shared "gathering level"). The
 * level-up notification always names the job tied to the activity that leveled, never the
 * player's currently equipped (possibly combat) job - that was the source of mining/farming
 * incorrectly announcing a combat job level-up.
 */
public final class GatheringLevelService {

    private static final String NO_JOB_LABEL = "無職";

    private final PlayerDataManager playerDataManager;
    private final GatheringLevelingConfig levelingConfig;
    private final JobManager jobManager;

    public GatheringLevelService(PlayerDataManager playerDataManager, GatheringLevelingConfig levelingConfig,
                                  JobManager jobManager) {
        this.playerDataManager = playerDataManager;
        this.levelingConfig = levelingConfig;
        this.jobManager = jobManager;
    }

    /** Adds experience to the player's level for {@code activity}, applying every level-up earned. */
    public void addExperience(UUID uuid, GatherActionType activity, long amount) {
        if (amount <= 0) {
            return;
        }
        playerDataManager.get(uuid).ifPresent(data -> {
            PlayerGatheringComponent component = data.require(PlayerGatheringComponent.class);
            int maxLevel = levelingConfig.getMaxLevel();
            if (component.getLevel(activity) >= maxLevel) {
                return;
            }
            int previousLevel = component.getLevel(activity);
            long experience = component.getExperience(activity) + amount;
            int level = previousLevel;
            while (level < maxLevel && experience >= levelingConfig.requiredExperience(level)) {
                experience -= levelingConfig.requiredExperience(level);
                level++;
            }
            component.setLevel(activity, level);
            component.setExperience(activity, level >= maxLevel ? 0 : experience);
            if (level > previousLevel) {
                announceLevelUp(uuid, activity, level);
            }
        });
    }

    /** Plays a level-up sound and notifies the player privately of their new job level. */
    private void announceLevelUp(UUID uuid, GatherActionType activity, int newLevel) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        String jobName = jobManager.getDefinition(activity.jobType()).map(Job::getDisplayName).orElse(NO_JOB_LABEL);
        player.sendMessage(Component.text(player.getName() + ":" + jobName + "のレベルが" + newLevel + "に上がりました", NamedTextColor.GREEN));
    }

    public int getLevel(UUID uuid, GatherActionType activity) {
        return playerDataManager.get(uuid)
                .flatMap(data -> data.component(PlayerGatheringComponent.class))
                .map(component -> component.getLevel(activity))
                .orElse(1);
    }
}
