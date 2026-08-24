package rpg.monster.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SlimeSplitEvent;
import rpg.monster.service.MonsterSpawnService;

/**
 * Cancels vanilla slime-splits for a tagged Orelia slime (e.g. {@code forest_slime}) on death.
 * A split's child slimes are ordinary vanilla mobs - no Orelia tag, no scaled stats, no health
 * bar, invisible to {@link MonsterDeathListener}'s reward/spawn-point-slot-release logic - so
 * they showed up as untracked, unrewarding, effectively "broken" monsters standing around after
 * every slime kill. Only gates on the dying slime actually being one of ours; an untagged wild
 * slime (if one somehow exists despite {@link VanillaHostileSpawnBlockerListener} normally
 * preventing that) still splits normally.
 */
public final class MonsterSlimeSplitListener implements Listener {

    private final MonsterSpawnService spawnService;

    public MonsterSlimeSplitListener(MonsterSpawnService spawnService) {
        this.spawnService = spawnService;
    }

    @EventHandler
    public void onSplit(SlimeSplitEvent event) {
        LivingEntity parent = event.getEntity();
        if (spawnService.dataOf(parent).isPresent()) {
            event.setCancelled(true);
        }
    }
}
