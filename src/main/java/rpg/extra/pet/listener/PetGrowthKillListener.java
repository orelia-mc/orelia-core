package rpg.extra.pet.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import rpg.api.CombatApi;
import rpg.extra.pet.config.PetGrowthLevelingConfig;
import rpg.extra.pet.manager.PetManager;
import rpg.extra.pet.service.PetGrowthService;
import rpg.extra.pet.service.PetService;

/**
 * Grants growth XP to the killer's currently-summoned/-selected pet for each tagged Orelia
 * monster kill, same "is this a tagged monster" gate via {@link CombatApi#identifyMonster}
 * that {@code rpg.quest.listener.QuestKillListener} uses - a vanilla passive mob never grants
 * this, so grinding cows for pet XP isn't a thing.
 */
public final class PetGrowthKillListener implements Listener {

    private final CombatApi combatApi;
    private final PetManager petManager;
    private final PetService petService;
    private final PetGrowthService growthService;
    private final PetGrowthLevelingConfig levelingConfig;

    public PetGrowthKillListener(CombatApi combatApi, PetManager petManager, PetService petService,
                                  PetGrowthService growthService, PetGrowthLevelingConfig levelingConfig) {
        this.combatApi = combatApi;
        this.petManager = petManager;
        this.petService = petService;
        this.growthService = growthService;
        this.levelingConfig = levelingConfig;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !petManager.hasActivePet(killer.getUniqueId())) {
            return;
        }
        if (combatApi.identifyMonster(event.getEntity()).isEmpty()) {
            return;
        }
        String petId = petService.getSelectedPetId(killer.getUniqueId());
        if (petId == null) {
            return;
        }
        growthService.addExperience(killer.getUniqueId(), petId, levelingConfig.getXpPerKill());
    }
}
