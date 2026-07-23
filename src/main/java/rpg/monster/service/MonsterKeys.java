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
    private final NamespacedKey targetLevel;

    public MonsterKeys(Plugin plugin) {
        this.monsterId = new NamespacedKey(plugin, "monster_id");
        this.spawnPointId = new NamespacedKey(plugin, "spawn_point_id");
        this.scaledCurrentHp = new NamespacedKey(plugin, "scaled_current_hp");
        this.targetLevel = new NamespacedKey(plugin, "target_level");
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

    /**
     * The target level a spawn point tagged this instance with (SOW: per-spawn-point monster
     * level scaling), used to scale its hp/attack-power/defense from the {@code monsters.yml}
     * template via {@link rpg.monster.config.MonsterLevelScalingConfig}. Absent means "no
     * scaling" - use the template value as-is.
     */
    public NamespacedKey targetLevel() {
        return targetLevel;
    }
}
