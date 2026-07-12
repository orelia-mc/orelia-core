package rpg.monster.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterDropService;
import rpg.monster.service.MonsterSpawnService;
import rpg.monster.spawnpoint.service.MonsterSpawnPointService;

/**
 * Rolls the drop table and grants EXP/money to the killer when a tagged monster dies, and
 * frees up its spawn point's alive-count slot (if it came from one) regardless of killer.
 */
public final class MonsterDeathListener implements Listener {

    private final MonsterSpawnService spawnService;
    private final MonsterDropService dropService;
    private final MonsterSpawnPointService spawnPointService;

    public MonsterDeathListener(MonsterSpawnService spawnService, MonsterDropService dropService,
                                 MonsterSpawnPointService spawnPointService) {
        this.spawnService = spawnService;
        this.dropService = dropService;
        this.spawnPointService = spawnPointService;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        MonsterData data = spawnService.dataOf(event.getEntity()).orElse(null);
        if (data == null) {
            return;
        }
        spawnPointService.onEntityRemoved(event.getEntity());

        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        dropService.rewardKiller(data, killer, event.getEntity().getLocation());
    }
}
