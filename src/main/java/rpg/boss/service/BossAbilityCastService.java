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
import rpg.gui.service.ActionBarService;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterSpawnService;
import rpg.status.combat.DamageFormula;

import java.util.Optional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically casts a currently-tracked boss's {@link BossAbility}s at nearby players (SOW
 * follow-up: "スキルを発動するボス"). Damage is delivered via the damager-carrying
 * {@code player.damage(amount, boss)} so it still runs through
 * {@code rpg.monster.listener.CombatDamageListener} (DEF/crit/weakness, and the scaled-to-vanilla
 * health conversion) - {@link DamageFormula#ABILITY_OVERRIDE_METADATA} tells that listener the
 * base amount is already resolved (the boss's own attack power times {@link BossAbility#getDamage()}
 * as a multiplier) rather than substituting its plain melee attack power.
 */
public final class BossAbilityCastService {

    public static final String FIREBALL_METADATA = "orelia_boss_ability_fireball";

    private static final double AGGRO_RANGE = 24.0;
    private static final long ANNOUNCE_ACTION_BAR_DURATION_MILLIS = 2500L;

    private final Plugin plugin;
    private final MonsterSpawnService monsterSpawnService;
    private final BossRepository bossRepository;
    private final Map<UUID, LivingEntity> activeBosses = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> lastCastAtMillis = new ConcurrentHashMap<>();
    private ActionBarService actionBarService;

    public BossAbilityCastService(Plugin plugin, MonsterSpawnService monsterSpawnService, BossRepository bossRepository) {
        this.plugin = plugin;
        this.monsterSpawnService = monsterSpawnService;
        this.bossRepository = bossRepository;
    }

    /**
     * Wired in from {@code GuiModule.onEnable} rather than the constructor, since
     * {@code ActionBarService} doesn't exist until the gui module enables (Boss registers
     * before Gui) - same reason {@code SkillActivationListener} is registered from GuiModule
     * instead of SkillModule.
     */
    public void setActionBarService(ActionBarService actionBarService) {
        this.actionBarService = actionBarService;
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
            // Ability casts happen too often (once per cooldown, per boss) to keep spamming
            // chat - routed through the action-bar HUD instead so it doesn't bury player chat
            // mid-fight. No-ops if the gui module hasn't wired ActionBarService in yet.
            if (actionBarService != null) {
                String message = ability.getAnnounceMessage();
                nearby.forEach(player -> actionBarService.showTransient(player, message, ANNOUNCE_ACTION_BAR_DURATION_MILLIS));
            }
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
        double scaledDamage = abilityDamage(boss, ability);
        for (Player player : nearby) {
            if (player.getLocation().distance(boss.getLocation()) <= ability.getRadius()) {
                boss.setMetadata(DamageFormula.ABILITY_OVERRIDE_METADATA, new FixedMetadataValue(plugin, true));
                try {
                    player.damage(scaledDamage, boss);
                } finally {
                    boss.removeMetadata(DamageFormula.ABILITY_OVERRIDE_METADATA, plugin);
                }
            }
        }
    }

    private void castFireballBarrage(LivingEntity boss, BossAbility ability, Collection<Player> nearby) {
        playSound(boss.getWorld(), boss, ability.getSound());
        double scaledDamage = abilityDamage(boss, ability);
        for (Player target : nearby) {
            Vector direction = target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector()).normalize();
            SmallFireball fireball = boss.getWorld().spawn(boss.getEyeLocation(), SmallFireball.class, projectile -> {
                projectile.setShooter(boss);
                projectile.setDirection(direction);
                projectile.setIsIncendiary(false);
                projectile.setYield(0f);
            });
            fireball.setMetadata(FIREBALL_METADATA, new FixedMetadataValue(plugin, new double[] {scaledDamage, ability.getRadius()}));
        }
    }

    /**
     * {@link BossAbility#getDamage()} is a multiplier on the boss's own (level-scaled) attack
     * power, not an absolute amount - keeps ability damage in step with
     * {@code MonsterLevelScalingConfig} the same way the boss's plain melee attack already is.
     * Falls back to the raw configured value if the boss has no resolvable {@code MonsterData}
     * (shouldn't normally happen for a spawned, tracked boss).
     */
    private double abilityDamage(LivingEntity boss, BossAbility ability) {
        Optional<MonsterData> data = monsterSpawnService.dataOf(boss);
        return data.map(d -> ability.getDamage() * monsterSpawnService.scaledAttackPowerOf(boss, d))
                .orElse(ability.getDamage());
    }

    private void playParticle(World world, LivingEntity boss, String particleName) {
        try {
            world.spawnParticle(Particle.valueOf(particleName), boss.getLocation().add(0, 1, 0), 60, 1.5, 1, 1.5, 0.05);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // bosses.yml stores legacy enum-style sound names (e.g. ENTITY_BLAZE_SHOOT) rather than
    // namespaced keys - Sound.valueOf is deprecated but remains the only lossless way to
    // resolve those without maintaining our own legacy-name-to-key table (a mechanical
    // "_" -> "." rewrite is wrong for names like ENTITY_IRON_GOLEM_ATTACK, whose real key is
    // entity.iron_golem.attack).
    @SuppressWarnings("deprecation")
    private void playSound(World world, LivingEntity boss, String soundName) {
        try {
            world.playSound(boss.getLocation(), Sound.valueOf(soundName), 2.0f, 0.9f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
