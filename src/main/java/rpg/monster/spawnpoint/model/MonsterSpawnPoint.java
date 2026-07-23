package rpg.monster.spawnpoint.model;

import java.util.UUID;

/**
 * An admin-placed location that periodically spawns a monster, up to {@code maxAlive}
 * concurrently alive at once, replacing natural/dungeon spawning (neither of which exists
 * yet - see {@code MonsterSpawnService}).
 */
public final class MonsterSpawnPoint {

    private final UUID id;
    private final String monsterId;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final int intervalSeconds;
    private final int maxAlive;
    private final Integer targetLevel;

    public MonsterSpawnPoint(UUID id, String monsterId, String world, double x, double y, double z,
                              int intervalSeconds, int maxAlive, Integer targetLevel) {
        this.id = id;
        this.monsterId = monsterId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.intervalSeconds = intervalSeconds;
        this.maxAlive = maxAlive;
        this.targetLevel = targetLevel;
    }

    public UUID getId() {
        return id;
    }

    public String getMonsterId() {
        return monsterId;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public int getMaxAlive() {
        return maxAlive;
    }

    /** Scales spawned monsters' hp/attack-power/defense (see {@code MonsterLevelScalingConfig}); {@code null} means "no scaling". */
    public Integer getTargetLevel() {
        return targetLevel;
    }
}
