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
import rpg.util.ColorUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Spawns the vanilla entity backing a {@link MonsterData} definition, tags it with the
 * monster id, and applies its configured HP. Attack/defense are applied per-hit by
 * {@link rpg.monster.listener.CombatDamageListener} instead of vanilla attributes, so
 * status-module ATK/DEF and monster defense compose the same way on both sides of a fight.
 * The nametag doubles as a live HP bar (see {@link MonsterHealthBarRenderer}) - set to full
 * HP here, updated after every hit by {@code MonsterHealthBarListener}.
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

        var maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = data.getHp();
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
            maxHealth = maxHealthAttribute.getValue();
        }
        entity.setHealth(Math.min(data.getHp(), maxHealth));
        updateHealthBar(entity, data, entity.getHealth(), maxHealth);

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
