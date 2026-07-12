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

    public MonsterKeys(Plugin plugin) {
        this.monsterId = new NamespacedKey(plugin, "monster_id");
        this.spawnPointId = new NamespacedKey(plugin, "spawn_point_id");
    }

    public NamespacedKey monsterId() {
        return monsterId;
    }

    public NamespacedKey spawnPointId() {
        return spawnPointId;
    }
}
