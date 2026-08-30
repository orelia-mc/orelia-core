package rpg.monster.service;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import rpg.boss.service.BossAbilityCastService;
import rpg.monster.model.MonsterAbility;
import rpg.monster.model.MonsterData;
import rpg.status.combat.DamageFormula;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically casts a currently-tracked regular monster's {@link MonsterAbility}s at nearby
 * players - the same idea as {@code rpg.boss.service.BossAbilityCastService}, kept as a fully
 * separate class rather than shared/generalized so bosses' already-shipped AI carries zero
 * regression risk from this addition. Damage is delivered via the damager-carrying
 * {@code player.damage(amount, monster)} so it still runs through
 * {@code rpg.monster.listener.CombatDamageListener} (DEF/crit/weakness, and the scaled-to-vanilla
 * health conversion) - {@link DamageFormula#ABILITY_OVERRIDE_METADATA} tells that listener the
 * base amount is already resolved (the monster's own attack power times
 * {@link MonsterAbility#getDamage()} as a multiplier) rather than substituting its plain melee
 * attack power.
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
    private static final int SAFE_LOCATION_ATTEMPTS = 8;

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

    // Deliberately no chat broadcast here: a monster casting the same ability every few
    // seconds would spam every nearby player's chat. Sound + particle carry the "an ability
    // is happening" signal instead - do not reintroduce a player.sendMessage(...) announcement
    // for regular monster abilities.
    private void cast(LivingEntity monster, MonsterAbility ability, Collection<Player> nearby) {
        switch (ability.getType()) {
            case AOE_SLAM -> castAoeSlam(monster, ability, nearby);
            case FIREBALL_BARRAGE -> castFireballBarrage(monster, ability, nearby);
            case TELEPORT -> castTeleport(monster, ability, nearby);
            case DEBUFF -> castDebuff(monster, ability, nearby);
            case SUMMON -> castSummon(monster, ability);
        }
    }

    private void castAoeSlam(LivingEntity monster, MonsterAbility ability, Collection<Player> nearby) {
        World world = monster.getWorld();
        playParticle(world, monster, ability.getParticle());
        playSound(world, monster, ability.getSound());
        double scaledDamage = abilityDamage(monster, ability);
        for (Player player : nearby) {
            if (player.getLocation().distance(monster.getLocation()) <= ability.getRadius()) {
                monster.setMetadata(DamageFormula.ABILITY_OVERRIDE_METADATA, new FixedMetadataValue(plugin, true));
                try {
                    player.damage(scaledDamage, monster);
                } finally {
                    monster.removeMetadata(DamageFormula.ABILITY_OVERRIDE_METADATA, plugin);
                }
            }
        }
    }

    private void castFireballBarrage(LivingEntity monster, MonsterAbility ability, Collection<Player> nearby) {
        playSound(monster.getWorld(), monster, ability.getSound());
        double scaledDamage = abilityDamage(monster, ability);
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
                    new FixedMetadataValue(plugin, new double[] {scaledDamage, ability.getRadius()}));
        }
    }

    /** Ambush repositioning: teleports next to a random nearby player rather than dealing damage directly. */
    private void castTeleport(LivingEntity monster, MonsterAbility ability, Collection<Player> nearby) {
        Player target = randomOf(nearby);
        if (target == null) {
            return;
        }
        Location destination = findSafeLocationNear(target.getLocation(), Math.max(1.5, ability.getRadius()));
        if (destination == null) {
            return; // no passable spot found nearby after SAFE_LOCATION_ATTEMPTS tries - skip this cast
        }
        playParticle(monster.getWorld(), monster, ability.getParticle());
        monster.teleport(destination);
        playSound(destination.getWorld(), monster, ability.getSound());
        playParticle(destination.getWorld(), monster, ability.getParticle());
    }

    /** Status-inflicting attack: applies a potion effect to everyone in range, no direct damage. */
    private void castDebuff(LivingEntity monster, MonsterAbility ability, Collection<Player> nearby) {
        PotionEffectType effectType = resolveEffectType(ability.getEffectType());
        if (effectType == null) {
            return; // misconfigured monsters.yml entry - fail closed, same as levelUpWeapon's material parse
        }
        playParticle(monster.getWorld(), monster, ability.getParticle());
        playSound(monster.getWorld(), monster, ability.getSound());
        int durationTicks = Math.max(1, ability.getEffectDurationSeconds()) * 20;
        for (Player player : nearby) {
            if (player.getLocation().distance(monster.getLocation()) <= ability.getRadius()) {
                player.addPotionEffect(new PotionEffect(effectType, durationTicks, ability.getEffectAmplifier()));
            }
        }
    }

    /** Spawns {@link MonsterAbility#getSummonCount()} copies of {@link MonsterAbility#getSummonMonsterId()} as reinforcements, scaled to this monster's own target level. */
    private void castSummon(LivingEntity monster, MonsterAbility ability) {
        String summonMonsterId = ability.getSummonMonsterId();
        if (summonMonsterId == null || summonMonsterId.isBlank()) {
            return; // misconfigured monsters.yml entry - fail closed
        }
        playParticle(monster.getWorld(), monster, ability.getParticle());
        playSound(monster.getWorld(), monster, ability.getSound());
        Integer targetLevel = monsterSpawnService.targetLevelOf(monster).orElse(null);
        for (int i = 0; i < ability.getSummonCount(); i++) {
            Location spawnLocation = findSafeLocationNear(monster.getLocation(), Math.max(2.0, ability.getRadius()));
            if (spawnLocation == null) {
                continue;
            }
            monsterSpawnService.spawn(summonMonsterId, spawnLocation, null, targetLevel)
                    .ifPresent(summoned -> monsterSpawnService.dataOf(summoned).ifPresent(data -> registerIfAble(summoned, data)));
        }
    }

    /**
     * A random passable (non-solid, headroom-clear) location within {@code radius} of
     * {@code center}, reusing {@code center}'s Y - both callers (teleport, summon) start from a
     * location a living entity is already standing at, so that Y is already known-walkable and
     * doesn't need its own ground search. {@code null} if nothing passable turns up within
     * {@link #SAFE_LOCATION_ATTEMPTS} random tries (e.g. a monster cornered against solid
     * terrain) - callers skip that single cast rather than teleporting into a wall.
     */
    private Location findSafeLocationNear(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < SAFE_LOCATION_ATTEMPTS; attempt++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            double distance = random.nextDouble(1.0, radius);
            Location candidate = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Block feet = candidate.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            if (feet.isPassable() && head.isPassable()) {
                return candidate;
            }
        }
        return null;
    }

    private Player randomOf(Collection<Player> players) {
        if (players.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(players.size());
        int i = 0;
        for (Player player : players) {
            if (i++ == index) {
                return player;
            }
        }
        throw new IllegalStateException("unreachable: index within players.size()");
    }

    // monsters.yml stores the effect as a plain PotionEffectType key name (e.g. POISON) -
    // getByName is deprecated in favor of Registry-based lookup, but kept here for the same
    // reason Sound.valueOf is kept in playSound below: the only lossless way to resolve a
    // legacy-style name without maintaining a separate name-to-key table. A misconfigured name
    // fails just this one ability (null), not the whole monster load.
    @SuppressWarnings("deprecation")
    private PotionEffectType resolveEffectType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return PotionEffectType.getByName(name.trim().toUpperCase());
    }

    /**
     * {@link MonsterAbility#getDamage()} is a multiplier on the monster's own (level-scaled)
     * attack power, not an absolute amount - keeps ability damage in step with
     * {@code MonsterLevelScalingConfig} the same way the monster's plain melee attack already is.
     * Falls back to the raw configured value if the monster has no resolvable {@code MonsterData}
     * (shouldn't normally happen for a spawned, tracked monster).
     */
    private double abilityDamage(LivingEntity monster, MonsterAbility ability) {
        Optional<MonsterData> data = monsterSpawnService.dataOf(monster);
        return data.map(d -> ability.getDamage() * monsterSpawnService.scaledAttackPowerOf(monster, d))
                .orElse(ability.getDamage());
    }

    private void playParticle(World world, LivingEntity monster, String particleName) {
        try {
            world.spawnParticle(Particle.valueOf(particleName), monster.getLocation().add(0, 1, 0), 60, 1.5, 1, 1.5, 0.05);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // monsters.yml stores legacy enum-style sound names (e.g. ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR)
    // rather than namespaced keys - Sound.valueOf is deprecated but remains the only lossless
    // way to resolve those without maintaining our own legacy-name-to-key table (a mechanical
    // "_" -> "." rewrite is wrong for names like ENTITY_EXPERIENCE_ORB_PICKUP, whose real key is
    // entity.experience_orb.pickup).
    @SuppressWarnings("deprecation")
    private void playSound(World world, LivingEntity monster, String soundName) {
        try {
            world.playSound(monster.getLocation(), Sound.valueOf(soundName), 0.6f, 0.9f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
