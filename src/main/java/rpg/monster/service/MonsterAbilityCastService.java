package rpg.monster.service;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import rpg.boss.service.BossAbilityCastService;
import rpg.monster.model.MonsterAbility;
import rpg.monster.model.MonsterData;
import rpg.util.ColorUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically casts a currently-tracked regular monster's {@link MonsterAbility}s at nearby
 * players - the same idea as {@code rpg.boss.service.BossAbilityCastService}, kept as a fully
 * separate class rather than shared/generalized so bosses' already-shipped AI carries zero
 * regression risk from this addition. Damage is applied via {@code player.damage(amount)} with
 * no damager entity, same rationale as the boss version: sidesteps
 * {@code rpg.monster.listener.CombatDamageListener}, which would otherwise overwrite an
 * ability's damage with the monster's plain melee attack power the same way it does for any
 * other monster hit.
 *
 * <p>Entities are registered here only at the actual spawn call sites that produce regular
 * (non-boss) monsters - {@code MonsterSpawnPointService#tick} and
 * {@code AdminCommand#spawnMonster} - never inside {@link MonsterSpawnService#spawn} itself,
 * to avoid a circular constructor dependency (this service already depends on
 * {@link MonsterSpawnService} to resolve a tracked entity's {@link MonsterData}). Boss-spawned
 * entities (via {@code BossModule#spawn}) are never registered here, which also means a
 * monsters.yml entry that's also a boss's {@code monster-id} target can never double-cast
 * between this service and {@code BossAbilityCastService}.
 */
public final class MonsterAbilityCastService {

    private static final double AGGRO_RANGE = 24.0;

    private final Plugin plugin;
    private final MonsterSpawnService monsterSpawnService;
    private final Map<UUID, LivingEntity> activeMonsters = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> lastCastAtMillis = new ConcurrentHashMap<>();

    public MonsterAbilityCastService(Plugin plugin, MonsterSpawnService monsterSpawnService) {
        this.plugin = plugin;
        this.monsterSpawnService = monsterSpawnService;
    }

    public void register(LivingEntity entity) {
        activeMonsters.put(entity.getUniqueId(), entity);
    }

    public void unregister(UUID entityId) {
        activeMonsters.remove(entityId);
        lastCastAtMillis.remove(entityId);
    }

    /** Registers {@code entity} only if {@code data} actually has abilities configured - avoids registry bloat for the common case. */
    public void registerIfAble(LivingEntity entity, MonsterData data) {
        if (!data.getAbilities().isEmpty()) {
            register(entity);
        }
    }

    /** Call periodically (e.g. every 20 ticks). Casts at most one due ability per monster per call. */
    public void tick() {
        for (LivingEntity monster : activeMonsters.values()) {
            if (monster.isDead() || !monster.isValid()) {
                unregister(monster.getUniqueId());
                continue;
            }
            MonsterData data = monsterSpawnService.dataOf(monster).orElse(null);
            if (data == null || data.getAbilities().isEmpty()) {
                continue;
            }
            List<Player> nearby = monster.getWorld().getNearbyPlayers(monster.getLocation(), AGGRO_RANGE).stream().toList();
            if (nearby.isEmpty()) {
                continue;
            }
            for (MonsterAbility ability : data.getAbilities()) {
                if (isOnCooldown(monster.getUniqueId(), ability)) {
                    continue;
                }
                cast(monster, ability, nearby);
                lastCastAtMillis.computeIfAbsent(monster.getUniqueId(), id -> new ConcurrentHashMap<>())
                        .put(ability.getId(), System.currentTimeMillis());
                break;
            }
        }
    }

    private boolean isOnCooldown(UUID monsterId, MonsterAbility ability) {
        long last = lastCastAtMillis.getOrDefault(monsterId, Map.of()).getOrDefault(ability.getId(), 0L);
        return System.currentTimeMillis() - last < ability.getCooldownSeconds() * 1000L;
    }

    private void cast(LivingEntity monster, MonsterAbility ability, Collection<Player> nearby) {
        if (ability.getAnnounceMessage() != null && !ability.getAnnounceMessage().isBlank()) {
            String message = ColorUtil.colorize(ability.getAnnounceMessage());
            nearby.forEach(player -> player.sendMessage(message));
        }
        switch (ability.getType()) {
            case AOE_SLAM -> castAoeSlam(monster, ability, nearby);
            case FIREBALL_BARRAGE -> castFireballBarrage(monster, ability, nearby);
        }
    }

    private void castAoeSlam(LivingEntity monster, MonsterAbility ability, Collection<Player> nearby) {
        World world = monster.getWorld();
        playParticle(world, monster, ability.getParticle());
        playSound(world, monster, ability.getSound());
        for (Player player : nearby) {
            if (player.getLocation().distance(monster.getLocation()) <= ability.getRadius()) {
                player.damage(ability.getDamage());
            }
        }
    }

    private void castFireballBarrage(LivingEntity monster, MonsterAbility ability, Collection<Player> nearby) {
        playSound(monster.getWorld(), monster, ability.getSound());
        for (Player target : nearby) {
            Vector direction = target.getEyeLocation().toVector().subtract(monster.getEyeLocation().toVector()).normalize();
            SmallFireball fireball = monster.getWorld().spawn(monster.getEyeLocation(), SmallFireball.class, projectile -> {
                projectile.setShooter(monster);
                projectile.setDirection(direction);
                projectile.setIsIncendiary(false);
                projectile.setYield(0f);
            });
            // Reuses the boss ability system's fireball-impact listener (BossFireballHitListener) -
            // its logic only reads the double[]{damage, radius} payload off this metadata key and
            // has no boss-specific behavior, so it's safe to share rather than duplicate.
            fireball.setMetadata(BossAbilityCastService.FIREBALL_METADATA,
                    new FixedMetadataValue(plugin, new double[] {ability.getDamage(), ability.getRadius()}));
        }
    }

    private void playParticle(World world, LivingEntity monster, String particleName) {
        try {
            world.spawnParticle(Particle.valueOf(particleName), monster.getLocation().add(0, 1, 0), 60, 1.5, 1, 1.5, 0.05);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playSound(World world, LivingEntity monster, String soundName) {
        try {
            world.playSound(monster.getLocation(), Sound.valueOf(soundName), 2.0f, 0.9f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
