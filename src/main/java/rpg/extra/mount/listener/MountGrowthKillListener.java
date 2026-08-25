package rpg.extra.mount.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import rpg.api.CombatApi;
import rpg.extra.mount.config.MountGrowthLevelingConfig;
import rpg.extra.mount.manager.MountManager;
import rpg.extra.mount.service.MountGrowthService;
import rpg.extra.mount.service.MountService;

/**
 * Grants growth XP to the killer's currently-summoned/-selected mount for each tagged Orelia
 * monster kill (e.g. while ridden into a mob for a melee kill) - same gate as
 * {@code rpg.extra.pet.listener.PetGrowthKillListener}.
 */
public final class MountGrowthKillListener implements Listener {

    private final CombatApi combatApi;
    private final MountManager mountManager;
    private final MountService mountService;
    private final MountGrowthService growthService;
    private final MountGrowthLevelingConfig levelingConfig;

    public MountGrowthKillListener(CombatApi combatApi, MountManager mountManager, MountService mountService,
                                    MountGrowthService growthService, MountGrowthLevelingConfig levelingConfig) {
        this.combatApi = combatApi;
        this.mountManager = mountManager;
        this.mountService = mountService;
        this.growthService = growthService;
        this.levelingConfig = levelingConfig;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !mountManager.hasActiveMount(killer.getUniqueId())) {
            return;
        }
        if (combatApi.identifyMonster(event.getEntity()).isEmpty()) {
            return;
        }
        String mountId = mountService.getSelectedMountId(killer.getUniqueId());
        if (mountId == null) {
            return;
        }
        growthService.addExperience(killer.getUniqueId(), mountId, levelingConfig.getXpPerKill());
    }
}
