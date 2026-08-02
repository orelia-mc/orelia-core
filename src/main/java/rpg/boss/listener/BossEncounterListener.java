package rpg.boss.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import rpg.boss.manager.BossRuntimeState;
import rpg.boss.manager.BossStateManager;
import rpg.boss.model.BossData;
import rpg.boss.model.BossPhase;
import rpg.boss.repository.BossRepository;
import rpg.boss.service.BossAbilityCastService;
import rpg.core.message.MessageManager;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterSpawnService;
import rpg.util.ColorUtil;

import java.time.Duration;
import java.util.List;

/**
 * Watches boss HP after damage resolves (MONITOR - read-only) and fires phase
 * announcements/enrage once thresholds are crossed. Cleans up runtime state on death.
 */
public final class BossEncounterListener implements Listener {

    private static final double BROADCAST_RADIUS = 48.0;
    private static final Title.Times ANNOUNCE_TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500));

    private final MonsterSpawnService monsterSpawnService;
    private final BossRepository bossRepository;
    private final BossStateManager stateManager;
    private final BossAbilityCastService abilityCastService;
    private final MessageManager messages;

    public BossEncounterListener(MonsterSpawnService monsterSpawnService, BossRepository bossRepository,
                                  BossStateManager stateManager, BossAbilityCastService abilityCastService,
                                  MessageManager messages) {
        this.monsterSpawnService = monsterSpawnService;
        this.bossRepository = bossRepository;
        this.stateManager = stateManager;
        this.abilityCastService = abilityCastService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        BossData boss = resolveBoss(entity);
        if (boss == null) {
            return;
        }

        var maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 1.0;
        double hpAfter = event.isCancelled() ? entity.getHealth() : Math.max(0, entity.getHealth() - event.getFinalDamage());
        double percent = maxHp <= 0 ? 0 : (hpAfter / maxHp) * 100.0;

        BossRuntimeState state = stateManager.stateOf(entity.getUniqueId());
        List<BossPhase> phases = boss.getPhases();
        while (state.getPhasesTriggered() < phases.size()
                && percent <= phases.get(state.getPhasesTriggered()).getHpThresholdPercent()) {
            BossPhase phase = phases.get(state.getPhasesTriggered());
            announce(entity, phase.getAnnounceMessage());
            entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation(), 10);
            state.incrementPhasesTriggered();
        }

        if (percent <= boss.getEnrageHpPercent() && !state.isEnraged()) {
            state.setEnraged(true);
            // entity.getName() is the live nametag, which doubles as the HP bar
            // (MonsterHealthBarRenderer) - use the clean MonsterData name instead, same reason
            // MonsterDeathListener does for the death message.
            String cleanName = monsterSpawnService.dataOf(entity).map(MonsterData::getName).orElse(entity.getName());
            announce(entity, messages.format("boss.enraged", "boss", cleanName));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        stateManager.clear(event.getEntity().getUniqueId());
        abilityCastService.unregister(event.getEntity().getUniqueId());
    }

    private BossData resolveBoss(LivingEntity entity) {
        return monsterSpawnService.idOf(entity).flatMap(bossRepository::findByMonsterId).orElse(null);
    }

    /**
     * Phase/enrage transitions are rare (a handful per boss fight) but important - shown as a
     * title instead of a chat line so they don't get lost mid-fight in the chat scroll, and
     * don't need to compete with {@link BossAbilityCastService}'s action-bar announcements.
     */
    private void announce(LivingEntity entity, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Component component = ColorUtil.component(message);
        Title title = Title.title(component, Component.empty(), ANNOUNCE_TITLE_TIMES);
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) <= BROADCAST_RADIUS * BROADCAST_RADIUS) {
                player.showTitle(title);
            }
        }
    }
}
