package rpg.dungeon.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import rpg.dungeon.service.DungeonEncounterService;

/**
 * Feeds every entity death into {@link DungeonEncounterService#onMobDeath}, which is a no-op
 * unless the entity is a tracked dungeon mob. Deliberately killer-agnostic (no
 * {@code combatApi.identifyMonster} check like {@code QuestKillListener}): the same
 * monsters.yml id can be used by multiple dungeons, so "what died" doesn't tell you which
 * instance it belonged to, and a kill should count toward clearing regardless of whether a
 * player or something else (fire, another party member's AoE) landed the final blow.
 */
public final class DungeonMobDeathListener implements Listener {

    private final DungeonEncounterService encounterService;

    public DungeonMobDeathListener(DungeonEncounterService encounterService) {
        this.encounterService = encounterService;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        encounterService.onMobDeath(event.getEntity().getUniqueId());
    }
}
