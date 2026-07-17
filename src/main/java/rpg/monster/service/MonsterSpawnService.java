package rpg.monster.service;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import rpg.monster.model.AiType;
import rpg.monster.model.MonsterData;
import rpg.monster.repository.MonsterRepository;
import rpg.util.ColorUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Spawns the vanilla entity backing a {@link MonsterData} definition, tags it with the
 * monster id, and applies its configured HP. Attack/defense are applied per-hit by
 * {@link rpg.monster.listener.MonsterCombatListener} instead of vanilla attributes, so
 * status-module ATK/DEF and monster defense compose the same way on both sides of a fight.
 */
public final class MonsterSpawnService {

    private final MonsterKeys keys;
    private final MonsterRepository repository;

    public MonsterSpawnService(MonsterKeys keys, MonsterRepository repository) {
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
        entity.customName(ColorUtil.component(data.getName()));
        entity.setCustomNameVisible(true);

        var maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = data.getHp();
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
            maxHealth = maxHealthAttribute.getValue();
        }
        entity.setHealth(Math.min(data.getHp(), maxHealth));

        if (entity instanceof Mob mob) {
            mob.setAware(data.getAiType() != AiType.PASSIVE);
        }

        return Optional.of(entity);
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
