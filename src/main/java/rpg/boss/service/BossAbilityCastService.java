package rpg.boss.service;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import rpg.boss.model.BossAbility;
import rpg.boss.model.BossData;
import rpg.boss.repository.BossRepository;
import rpg.monster.service.MonsterSpawnService;
import rpg.util.ColorUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically casts a currently-tracked boss's {@link BossAbility}s at nearby players (SOW
 * follow-up: "スキルを発動するボス"). Damage is applied via {@code player.damage(amount)}
 * with no damager entity - deliberately sidesteps
 * {@code rpg.monster.listener.MonsterCombatListener}, which would otherwise overwrite an
 * ability's damage with the boss's plain melee attack power the same way it does for any
 * other monster hit.
 */
public final class BossAbilityCastService {

    public static final String FIREBALL_METADATA = "orelia_boss_ability_fireball";

    private static final double AGGRO_RANGE = 24.0;

    private final Plugin plugin;
    private final MonsterSpawnService monsterSpawnService;
    private final BossRepository bossRepository;
    private final Map<UUID, LivingEntity> activeBosses = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> lastCastAtMillis = new ConcurrentHashMap<>();

    public BossAbilityCastService(Plugin plugin, MonsterSpawnService monsterSpawnService, BossRepository bossRepository) {
        this.plugin = plugin;
        this.monsterSpawnService = monsterSpawnService;
        this.bossRepository = bossRepository;
    }

    public void register(LivingEntity entity) {
        activeBosses.put(entity.getUniqueId(), entity);
    }

    public void unregister(UUID entityId) {
        activeBosses.remove(entityId);
        lastCastAtMillis.remove(entityId);
    }

    /** Call periodically (e.g. every 20 ticks). Casts at most one due ability per boss per call. */
    public void tick() {
        for (LivingEntity boss : activeBosses.values()) {
            if (boss.isDead() || !boss.isValid()) {
                unregister(boss.getUniqueId());
                continue;
            }
            BossData data = monsterSpawnService.idOf(boss).flatMap(bossRepository::findByMonsterId).orElse(null);
            if (data == null || data.getAbilities().isEmpty()) {
                continue;
            }
            List<Player> nearby = boss.getWorld().getNearbyPlayers(boss.getLocation(), AGGRO_RANGE).stream().toList();
            if (nearby.isEmpty()) {
                continue;
            }
            for (BossAbility ability : data.getAbilities()) {
                if (isOnCooldown(boss.getUniqueId(), ability)) {
                    continue;
                }
                cast(boss, ability, nearby);
                lastCastAtMillis.computeIfAbsent(boss.getUniqueId(), id -> new ConcurrentHashMap<>())
                        .put(ability.getId(), System.currentTimeMillis());
                break;
            }
        }
    }

    private boolean isOnCooldown(UUID bossId, BossAbility ability) {
        long last = lastCastAtMillis.getOrDefault(bossId, Map.of()).getOrDefault(ability.getId(), 0L);
        return System.currentTimeMillis() - last < ability.getCooldownSeconds() * 1000L;
    }

    private void cast(LivingEntity boss, BossAbility ability, Collection<Player> nearby) {
        if (ability.getAnnounceMessage() != null && !ability.getAnnounceMessage().isBlank()) {
            String message = ColorUtil.colorize(ability.getAnnounceMessage());
            nearby.forEach(player -> player.sendMessage(message));
        }
        switch (ability.getType()) {
            case AOE_SLAM -> castAoeSlam(boss, ability, nearby);
            case FIREBALL_BARRAGE -> castFireballBarrage(boss, ability, nearby);
        }
    }

    private void castAoeSlam(LivingEntity boss, BossAbility ability, Collection<Player> nearby) {
        World world = boss.getWorld();
        playParticle(world, boss, ability.getParticle());
        playSound(world, boss, ability.getSound());
        for (Player player : nearby) {
            if (player.getLocation().distance(boss.getLocation()) <= ability.getRadius()) {
                player.damage(ability.getDamage());
            }
        }
    }

    private void castFireballBarrage(LivingEntity boss, BossAbility ability, Collection<Player> nearby) {
        playSound(boss.getWorld(), boss, ability.getSound());
        for (Player target : nearby) {
            Vector direction = target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector()).normalize();
            SmallFireball fireball = boss.getWorld().spawn(boss.getEyeLocation(), SmallFireball.class, projectile -> {
                projectile.setShooter(boss);
                projectile.setDirection(direction);
                projectile.setIsIncendiary(false);
                projectile.setYield(0f);
            });
            fireball.setMetadata(FIREBALL_METADATA, new FixedMetadataValue(plugin, new double[] {ability.getDamage(), ability.getRadius()}));
        }
    }

    private void playParticle(World world, LivingEntity boss, String particleName) {
        try {
            world.spawnParticle(Particle.valueOf(particleName), boss.getLocation().add(0, 1, 0), 60, 1.5, 1, 1.5, 0.05);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playSound(World world, LivingEntity boss, String soundName) {
        try {
            world.playSound(boss.getLocation(), Sound.valueOf(soundName), 2.0f, 0.9f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
