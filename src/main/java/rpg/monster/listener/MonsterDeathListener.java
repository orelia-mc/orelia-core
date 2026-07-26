package rpg.monster.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import rpg.core.message.MessageManager;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterDropService;
import rpg.monster.service.MonsterSpawnService;
import rpg.monster.spawnpoint.service.MonsterSpawnPointService;
import rpg.status.combat.DamageFormula;
import rpg.util.ColorUtil;

/**
 * Rolls the drop table and grants EXP/money to the killer when a tagged monster dies, and
 * frees up its spawn point's alive-count slot (if it came from one) regardless of killer.
 * Also rewrites the death message when a player is killed by a tagged monster, since the
 * entity's live nametag ({@link rpg.monster.service.MonsterHealthBarRenderer}'s HP bar) would
 * otherwise leak into vanilla's default "X was slain by <killer's display name>" message.
 */
public final class MonsterDeathListener implements Listener {

    private final MonsterSpawnService spawnService;
    private final MonsterDropService dropService;
    private final MonsterSpawnPointService spawnPointService;
    private final MessageManager messages;

    public MonsterDeathListener(MonsterSpawnService spawnService, MonsterDropService dropService,
                                 MonsterSpawnPointService spawnPointService, MessageManager messages) {
        this.spawnService = spawnService;
        this.dropService = dropService;
        this.spawnPointService = spawnPointService;
        this.messages = messages;
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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        LivingEntity attacker = resolveAttacker(event.getEntity());
        if (attacker == null) {
            return;
        }
        MonsterData data = spawnService.dataOf(attacker).orElse(null);
        if (data == null) {
            return;
        }
        String formatted = messages.format("monster.death-message",
                "player", event.getEntity().getName(), "monster", data.getName());
        event.deathMessage(ColorUtil.component(formatted));
    }

    /**
     * The living entity actually responsible for the killing blow. Checked in two ways:
     * {@link DamageFormula#LAST_ABILITY_ATTACKER_METADATA_KEY} first, since a monster/boss
     * ability (AOE slam, fireball barrage) deals damage via a no-damager
     * {@code player.damage(amount)} call that never produces an {@link EntityDamageByEntityEvent}
     * for {@link #resolveDamager} to read a damager from at all - without this, those kills fell
     * through to vanilla's plain "X died" message with no killer attribution whatsoever. Falls
     * back to the real damager - itself for a melee attacker, or whoever fired it for a
     * projectile - for every other kind of hit.
     */
    private LivingEntity resolveAttacker(Player victim) {
        if (victim.hasMetadata(DamageFormula.LAST_ABILITY_ATTACKER_METADATA_KEY)) {
            Object value = victim.getMetadata(DamageFormula.LAST_ABILITY_ATTACKER_METADATA_KEY).get(0).value();
            if (value instanceof LivingEntity living && living.isValid()) {
                return living;
            }
        }
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        return resolveDamager(byEntity.getDamager());
    }

    /**
     * The living entity actually responsible for a hit - itself for a melee attacker, or
     * whoever fired it for a projectile. Without this, a player killed by a ranged monster
     * attack fell through as an unrecognized damager (a {@link Projectile} is never itself a
     * {@link LivingEntity}), leaving vanilla's own "X was slain by &lt;killer's live
     * nametag&gt;" message - HP-bar decoration and all - in place.
     */
    private LivingEntity resolveDamager(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }
}
