package rpg.monster.service;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * PersistentDataContainer keys used to tag spawned entities: their {@code monsters.yml} id,
 * and (for entities spawned by a recurring spawn point) which spawn point produced them.
 */
public final class MonsterKeys {

    private final NamespacedKey monsterId;
    private final NamespacedKey spawnPointId;
    private final NamespacedKey scaledCurrentHp;

    public MonsterKeys(Plugin plugin) {
        this.monsterId = new NamespacedKey(plugin, "monster_id");
        this.spawnPointId = new NamespacedKey(plugin, "spawn_point_id");
        this.scaledCurrentHp = new NamespacedKey(plugin, "scaled_current_hp");
    }

    public NamespacedKey monsterId() {
        return monsterId;
    }

    public NamespacedKey spawnPointId() {
        return spawnPointId;
    }

    /**
     * This monster instance's true current HP, which can exceed what's safe to store directly
     * in the vanilla {@code MAX_HEALTH} attribute (see {@code MonsterSpawnService}'s
     * {@code combat.scaled-health.vanilla-cap}) - vanilla health is kept proportional to this
     * instead of equal to it.
     */
    public NamespacedKey scaledCurrentHp() {
        return scaledCurrentHp;
    }
}
