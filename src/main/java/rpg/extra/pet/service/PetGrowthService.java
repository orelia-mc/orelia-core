package rpg.extra.pet.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import rpg.api.StatusApi;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.extra.pet.model.PetDefinition;
import rpg.extra.pet.model.PetDefinition.PetGrowthTemplate;
import rpg.extra.pet.model.PetGrowthComponent;
import rpg.extra.pet.repository.PetConfigRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies pet growth experience/level-ups (tracked independently per species, see
 * {@link PetGrowthComponent}) and reflects the current level's stat bonus onto the player via
 * {@link StatusApi#setEquipmentContribution} while the leveled species is the one actually
 * summoned - same "present while active, gone when cleared" shape as an accessory slot, not a
 * timed buff, so every set/clear here also gets vanilla-health resync for free (see
 * {@code StatusService#setEquipmentContribution}'s own doc comment).
 */
public final class PetGrowthService {

    /** Flat, not per-species - a player has at most one active pet at a time (see {@code PetManager}), same reasoning as Job's own flat {@code "job"} source key. */
    private static final String STATUS_SOURCE_KEY = "pet";

    private final PlayerDataManager playerDataManager;
    private final PetConfigRepository configRepository;
    private final StatusApi statusApi;
    private final MessageManager messages;

    public PetGrowthService(PlayerDataManager playerDataManager, PetConfigRepository configRepository,
                             StatusApi statusApi, MessageManager messages) {
        this.playerDataManager = playerDataManager;
        this.configRepository = configRepository;
        this.statusApi = statusApi;
        this.messages = messages;
    }

    /** Adds growth XP to {@code petId} for {@code ownerId}, applying every level-up earned, and reapplies the stat bonus if {@code petId} is currently summoned. */
    public void addExperience(UUID ownerId, String petId, long amount) {
        if (amount <= 0) {
            return;
        }
        PetDefinition definition = configRepository.findById(petId).orElse(null);
        if (definition == null) {
            return;
        }
        PetGrowthTemplate growth = definition.getGrowth();
        if (growth.maxLevel() <= 1 || growth.expPerLevel() <= 0) {
            return; // opt-out species (no growth: section) - PetGrowthTemplate#none()
        }
        playerDataManager.get(ownerId).ifPresent(data -> {
            PetGrowthComponent component = data.require(PetGrowthComponent.class);
            int previousLevel = component.getLevel(petId);
            if (previousLevel >= growth.maxLevel()) {
                return;
            }
            long experience = component.getExperience(petId) + amount;
            int level = previousLevel;
            while (level < growth.maxLevel() && experience >= growth.expPerLevel()) {
                experience -= growth.expPerLevel();
                level++;
            }
            component.setLevel(petId, level);
            component.setExperience(petId, level >= growth.maxLevel() ? 0 : experience);
            if (level > previousLevel) {
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null) {
                    messages.send(owner, "pet.growth.level-up", "name", definition.getName(), "level", level);
                }
                applyGrowthBonus(ownerId, petId);
            }
        });
    }

    /** Reflects {@code petId}'s current level's stat bonus onto the player - call on summon and after a level-up while summoned. No-op for an opt-out (no {@code growth:}) species. */
    public void applyGrowthBonus(UUID ownerId, String petId) {
        PetDefinition definition = configRepository.findById(petId).orElse(null);
        if (definition == null) {
            return;
        }
        PetGrowthTemplate growth = definition.getGrowth();
        if (growth.perLevelStats().isEmpty()) {
            return;
        }
        int level = getLevel(ownerId, petId);
        Map<String, Double> scaled = new HashMap<>();
        growth.perLevelStats().forEach((stat, perLevel) -> scaled.put(stat, perLevel * level));
        statusApi.setEquipmentContribution(ownerId, STATUS_SOURCE_KEY, scaled);
    }

    /** Clears the growth bonus - call whenever the player's pet despawns (see {@code PetManager#setOnDespawn}). */
    public void clearGrowthBonus(UUID ownerId) {
        statusApi.clearEquipmentContribution(ownerId, STATUS_SOURCE_KEY);
    }

    public int getLevel(UUID ownerId, String petId) {
        return playerDataManager.get(ownerId)
                .flatMap(data -> data.component(PetGrowthComponent.class))
                .map(component -> component.getLevel(petId))
                .orElse(1);
    }
}
