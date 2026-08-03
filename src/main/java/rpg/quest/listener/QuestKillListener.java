package rpg.quest.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import rpg.api.CombatApi;
import rpg.quest.model.ObjectiveType;
import rpg.quest.service.QuestProgressService;

/**
 * Feeds {@link ObjectiveType#KILL_MONSTER}/{@code KILL_BOSS} quest objectives from the
 * same death event orelia-core's monster module already listens to, via {@link CombatApi}
 * rather than quest owning its own copy of "is this a tagged monster" logic.
 */
public final class QuestKillListener implements Listener {

    private final CombatApi combatApi;
    private final QuestProgressService questProgressService;

    public QuestKillListener(CombatApi combatApi, QuestProgressService questProgressService) {
        this.combatApi = combatApi;
        this.questProgressService = questProgressService;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        String monsterId = combatApi.identifyMonster(event.getEntity()).orElse(null);
        if (monsterId == null) {
            return;
        }
        questProgressService.onMonsterKilled(killer.getUniqueId(), monsterId);
        combatApi.identifyBoss(monsterId).ifPresent(bossId -> questProgressService.onBossKilled(killer.getUniqueId(), bossId));
    }
}
