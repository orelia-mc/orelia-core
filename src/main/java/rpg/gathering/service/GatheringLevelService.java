package rpg.gathering.service;

import rpg.core.player.PlayerDataManager;
import rpg.gathering.config.GatheringLevelingConfig;
import rpg.gathering.model.PlayerGatheringComponent;

import java.util.UUID;

/**
 * Applies gathering/farming experience gains and level-ups shared by mining, woodcutting,
 * and farming (SOW 3.3).
 */
public final class GatheringLevelService {

    private final PlayerDataManager playerDataManager;
    private final GatheringLevelingConfig levelingConfig;

    public GatheringLevelService(PlayerDataManager playerDataManager, GatheringLevelingConfig levelingConfig) {
        this.playerDataManager = playerDataManager;
        this.levelingConfig = levelingConfig;
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
            long experience = component.getExperience() + amount;
            int level = component.getLevel();
            while (level < maxLevel && experience >= levelingConfig.requiredExperience(level)) {
                experience -= levelingConfig.requiredExperience(level);
                level++;
            }
            component.setLevel(level);
            component.setExperience(level >= maxLevel ? 0 : experience);
        });
    }

    public int getLevel(UUID uuid) {
        return playerDataManager.get(uuid)
                .flatMap(data -> data.component(PlayerGatheringComponent.class))
                .map(PlayerGatheringComponent::getLevel)
                .orElse(1);
    }
}
