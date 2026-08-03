package rpg.npc.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import rpg.npc.model.NpcData;
import rpg.npc.repository.NpcRepository;

import java.util.Optional;

/**
 * Spawns the vanilla entity backing an {@link NpcData} definition as a static, harmless
 * fixture: no AI pathing, invulnerable, silent - a "fake NPC" rather than a real mob.
 */
public final class NpcSpawnService {

    private final NpcKeys keys;
    private final NpcRepository repository;

    public NpcSpawnService(NpcKeys keys, NpcRepository repository) {
        this.keys = keys;
        this.repository = repository;
    }

    public Optional<Entity> spawn(String npcId, Location location) {
        NpcData data = repository.findById(npcId).orElse(null);
        if (data == null) {
            return Optional.empty();
        }

        Entity entity = location.getWorld().spawnEntity(location, data.getEntityType());
        entity.getPersistentDataContainer().set(keys.npcId(), PersistentDataType.STRING, data.getId());
        entity.setCustomName(data.getName());
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGravity(false);

        if (entity instanceof Mob mob) {
            mob.setAware(false);
            mob.setAI(false);
        }
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setCollidable(false);
        }

        return Optional.of(entity);
    }

    /**
     * The NPC id tagged onto {@code entity}, if it is one of ours. Falls back to the pre-merge
     * {@code oreliaworld:} key (see {@link NpcKeys#legacyNpcId()}) and re-stamps the entity under
     * the current namespace, so an NPC spawned before the 3-plugin merge keeps working and only
     * has to be migrated once.
     */
    public Optional<String> idOf(Entity entity) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        String id = container.get(keys.npcId(), PersistentDataType.STRING);
        if (id != null) {
            return Optional.of(id);
        }
        String legacyId = container.get(keys.legacyNpcId(), PersistentDataType.STRING);
        if (legacyId == null) {
            return Optional.empty();
        }
        container.set(keys.npcId(), PersistentDataType.STRING, legacyId);
        container.remove(keys.legacyNpcId());
        return Optional.of(legacyId);
    }

    public Optional<NpcData> dataOf(Entity entity) {
        return idOf(entity).flatMap(repository::findById);
    }

    /** Removes every entity tagged with {@code npcId} across every loaded world. */
    public boolean despawn(String npcId) {
        boolean removedAny = false;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (idOf(entity).map(npcId::equals).orElse(false)) {
                    entity.remove();
                    removedAny = true;
                }
            }
        }
        return removedAny;
    }
}
