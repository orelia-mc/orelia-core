package rpg.extra.mount.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import rpg.api.StatusApi;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.extra.mount.model.MountDefinition;
import rpg.extra.mount.model.MountDefinition.MountGrowthTemplate;
import rpg.extra.mount.model.MountGrowthComponent;
import rpg.extra.mount.repository.MountConfigRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies mount growth experience/level-ups and reflects the current level's stat bonus onto
 * the player via {@link StatusApi#setEquipmentContribution} while the leveled species is the
 * one summoned - same shape as {@code rpg.extra.pet.service.PetGrowthService}.
 */
public final class MountGrowthService {

    /** Flat, not per-species - a player has at most one active mount at a time (see {@code MountManager}). */
    private static final String STATUS_SOURCE_KEY = "mount";

    private final PlayerDataManager playerDataManager;
    private final MountConfigRepository configRepository;
    private final StatusApi statusApi;
    private final MessageManager messages;

    public MountGrowthService(PlayerDataManager playerDataManager, MountConfigRepository configRepository,
                               StatusApi statusApi, MessageManager messages) {
        this.playerDataManager = playerDataManager;
        this.configRepository = configRepository;
        this.statusApi = statusApi;
        this.messages = messages;
    }

    public void addExperience(UUID ownerId, String mountId, long amount) {
        if (amount <= 0) {
            return;
        }
        MountDefinition definition = configRepository.findById(mountId).orElse(null);
        if (definition == null) {
            return;
        }
        MountGrowthTemplate growth = definition.getGrowth();
        if (growth.maxLevel() <= 1 || growth.expPerLevel() <= 0) {
            return; // opt-out species (no growth: section) - MountGrowthTemplate#none()
        }
        playerDataManager.get(ownerId).ifPresent(data -> {
            MountGrowthComponent component = data.require(MountGrowthComponent.class);
            int previousLevel = component.getLevel(mountId);
            if (previousLevel >= growth.maxLevel()) {
                return;
            }
            long experience = component.getExperience(mountId) + amount;
            int level = previousLevel;
            while (level < growth.maxLevel() && experience >= growth.expPerLevel()) {
                experience -= growth.expPerLevel();
                level++;
            }
            component.setLevel(mountId, level);
            component.setExperience(mountId, level >= growth.maxLevel() ? 0 : experience);
            if (level > previousLevel) {
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null) {
                    messages.send(owner, "mount.growth.level-up", "name", definition.getName(), "level", level);
                }
                applyGrowthBonus(ownerId, mountId);
            }
        });
    }

    /** Reflects {@code mountId}'s current level's stat bonus onto the player - call on summon and after a level-up while summoned. */
    public void applyGrowthBonus(UUID ownerId, String mountId) {
        MountDefinition definition = configRepository.findById(mountId).orElse(null);
        if (definition == null) {
            return;
        }
        MountGrowthTemplate growth = definition.getGrowth();
        if (growth.perLevelStats().isEmpty()) {
            return;
        }
        int level = getLevel(ownerId, mountId);
        Map<String, Double> scaled = new HashMap<>();
        growth.perLevelStats().forEach((stat, perLevel) -> scaled.put(stat, perLevel * level));
        statusApi.setEquipmentContribution(ownerId, STATUS_SOURCE_KEY, scaled);
    }

    /** Clears the growth bonus - call whenever the player's mount despawns (see {@code MountManager#setOnDespawn}). */
    public void clearGrowthBonus(UUID ownerId) {
        statusApi.clearEquipmentContribution(ownerId, STATUS_SOURCE_KEY);
    }

    public int getLevel(UUID ownerId, String mountId) {
        return playerDataManager.get(ownerId)
                .flatMap(data -> data.component(MountGrowthComponent.class))
                .map(component -> component.getLevel(mountId))
                .orElse(1);
    }
}
