package rpg.monster.service;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import rpg.core.OreliaPlugin;
import rpg.monster.model.AiType;
import rpg.monster.model.MonsterData;
import rpg.monster.repository.MonsterRepository;
import rpg.status.service.ScaledHealthService;
import rpg.util.ColorUtil;
import rpg.util.MathUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Spawns the vanilla entity backing a {@link MonsterData} definition, tags it with the
 * monster id, and applies its configured HP. Attack/defense are applied per-hit by
 * {@link rpg.monster.listener.CombatDamageListener} instead of vanilla attributes, so
 * status-module ATK/DEF and monster defense compose the same way on both sides of a fight.
 * The nametag doubles as a live HP bar (see {@link MonsterHealthBarRenderer}) - set to full
 * HP here, updated after every hit by {@code MonsterHealthBarListener}.
 *
 * <p>{@code data.getHp()} is the monster's "scaled" HP (SOW's `monsters.yml` value, can run
 * into the thousands for a high-difficulty boss) - vanilla {@code Attribute.MAX_HEALTH} is
 * capped to a small, engine-safe range ({@code combat.scaled-health.vanilla-cap}) instead of
 * being set to the full scaled value directly, the same tradeoff players get (see
 * {@link ScaledHealthService}). The true current HP is tracked separately via
 * {@link MonsterKeys#scaledCurrentHp()} and kept proportional to vanilla health.
 */
public final class MonsterSpawnService {

    private final OreliaPlugin plugin;
    private final MonsterKeys keys;
    private final MonsterRepository repository;
    private final MonsterHealthBarRenderer healthBarRenderer = new MonsterHealthBarRenderer();

    public MonsterSpawnService(OreliaPlugin plugin, MonsterKeys keys, MonsterRepository repository) {
        this.plugin = plugin;
        this.keys = keys;
        this.repository = repository;
    }

    public Optional<LivingEntity> spawn(String monsterId, Location location) {
        return spawn(monsterId, location, null);
    }

    /** Also tags the entity with {@code spawnPointId} so {@link rpg.monster.spawnpoint.manager.MonsterSpawnPointManager} can track/cap it. */
    public Optional<LivingEntity> spawn(String monsterId, Location location, UUID spawnPointId) {
        MonsterData data = repository.findById(monsterId).orElse(null);
        if (data == null) {
            return Optional.empty();
        }

        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, data.getEntityType());
        entity.getPersistentDataContainer().set(keys.monsterId(), PersistentDataType.STRING, data.getId());
        if (spawnPointId != null) {
            entity.getPersistentDataContainer().set(keys.spawnPointId(), PersistentDataType.STRING, spawnPointId.toString());
        }
        entity.setCustomNameVisible(true);

        double vanillaCap = vanillaHealthCap();
        double vanillaMax = Math.min(data.getHp(), vanillaCap);
        var maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(vanillaMax);
            vanillaMax = maxHealthAttribute.getValue();
        }
        entity.setHealth(vanillaMax);
        setScaledCurrentHp(entity, data.getHp());
        updateHealthBar(entity, data, data.getHp(), data.getHp());

        if (entity instanceof Mob mob) {
            mob.setAware(data.getAiType() != AiType.PASSIVE);
        }

        return Optional.of(entity);
    }

    /** Re-renders {@code entity}'s nametag as a live HP bar; also used at spawn time (full HP). */
    public void updateHealthBar(LivingEntity entity, MonsterData data, double currentHp, double maxHp) {
        var config = plugin.getConfigManager().get("config.yml").get();
        if (!config.getBoolean("monster.health-bar.enabled", true)) {
            entity.customName(ColorUtil.component(data.getName()));
            return;
        }
        int length = config.getInt("monster.health-bar.length", 10);
        String format = config.getString("monster.health-bar.format", "{name} &7[{bar}&7] &f{current}/{max}");
        String filledColor = config.getString("monster.health-bar.filled-color", "&a");
        String emptyColor = config.getString("monster.health-bar.empty-color", "&8");
        String rendered = healthBarRenderer.render(data.getName(), currentHp, maxHp, length, format, filledColor, emptyColor);
        entity.customName(ColorUtil.component(rendered));
    }

    /** This monster instance's true current HP - defaults to {@code data.getHp()} (full) for a legacy entity spawned before this PDC value existed. */
    public double scaledCurrentHpOf(LivingEntity entity, MonsterData data) {
        Double value = entity.getPersistentDataContainer().get(keys.scaledCurrentHp(), PersistentDataType.DOUBLE);
        return value == null ? data.getHp() : value;
    }

    private void setScaledCurrentHp(LivingEntity entity, double value) {
        entity.getPersistentDataContainer().set(keys.scaledCurrentHp(), PersistentDataType.DOUBLE, value);
    }

    /**
     * Reduces this monster's tracked scaled HP by a combat-computed scaled damage amount -
     * called by {@code CombatDamageListener} alongside converting that same amount to vanilla
     * for {@code EntityDamageEvent#setDamage}, mirroring the player-side
     * {@code StatusService#applyScaledCombatDamage}. Does not touch vanilla health itself -
     * Bukkit's own event resolution applies the converted amount.
     */
    public void applyScaledCombatDamage(LivingEntity entity, MonsterData data, double scaledDamage) {
        double current = scaledCurrentHpOf(entity, data);
        setScaledCurrentHp(entity, Math.max(0, current - scaledDamage));
    }

    /**
     * For damage that never goes through {@code CombatDamageListener} (which only handles
     * {@code EntityDamageByEntityEvent}) - fall/fire/other environmental damage still reduces
     * vanilla health normally via Bukkit, so this just mirrors the same *percentage* loss onto
     * the tracked scaled HP, the same way {@code ScaledHealthRegenListener} mirrors vanilla
     * healing in the other direction.
     */
    public void applyEnvironmentalDamage(LivingEntity entity, MonsterData data, double vanillaDamage) {
        var maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double vanillaMax = maxHealthAttribute != null ? maxHealthAttribute.getValue() : entity.getHealth();
        if (vanillaMax <= 0) {
            return;
        }
        double scaledMax = data.getHp();
        double scaledDamage = (vanillaDamage / vanillaMax) * scaledMax;
        double current = scaledCurrentHpOf(entity, data);
        setScaledCurrentHp(entity, MathUtil.clamp(current - scaledDamage, 0, scaledMax));
    }

    private double vanillaHealthCap() {
        return plugin.getConfigManager().get("config.yml").get().getDouble("combat.scaled-health.vanilla-cap", 1024.0);
    }

    public Optional<String> idOf(LivingEntity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(keys.monsterId(), PersistentDataType.STRING));
    }

    public Optional<UUID> spawnPointIdOf(LivingEntity entity) {
        String raw = entity.getPersistentDataContainer().get(keys.spawnPointId(), PersistentDataType.STRING);
        return raw == null ? Optional.empty() : Optional.of(UUID.fromString(raw));
    }

    public Optional<MonsterData> dataOf(LivingEntity entity) {
        return idOf(entity).flatMap(repository::findById);
    }
}
