package rpg.dungeon.model;

import java.util.List;
import java.util.Map;

/**
 * Static dungeon definition loaded from {@code dungeons.yml}. {@link #arenas} lists the
 * physical entry points a run can be spawned at - one concurrent run per arena, so a
 * dungeon with 3 arenas can host up to 3 parties at once.
 */
public final class DungeonData {

    private final String id;
    private final String name;
    private final DungeonType type;
    private final int minPartySize;
    private final int maxPartySize;
    private final List<DungeonArena> arenas;
    private final long rewardExp;
    private final double rewardMoney;
    private final Map<String, Integer> enemies;
    private final String bossId;
    private final int timeLimitSeconds;

    public DungeonData(String id, String name, DungeonType type, int minPartySize, int maxPartySize,
                        List<DungeonArena> arenas, long rewardExp, double rewardMoney,
                        Map<String, Integer> enemies, String bossId, int timeLimitSeconds) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.minPartySize = minPartySize;
        this.maxPartySize = maxPartySize;
        this.arenas = List.copyOf(arenas);
        this.rewardExp = rewardExp;
        this.rewardMoney = rewardMoney;
        this.enemies = Map.copyOf(enemies);
        this.bossId = bossId;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DungeonType getType() {
        return type;
    }

    public int getMinPartySize() {
        return minPartySize;
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    /** Physical entry points this dungeon can spawn a run at - one concurrent run per arena. */
    public List<DungeonArena> getArenas() {
        return arenas;
    }

    public long getRewardExp() {
        return rewardExp;
    }

    public double getRewardMoney() {
        return rewardMoney;
    }

    /** monsters.yml id -> count required to clear. Empty if this dungeon has no regular enemies (boss-only). */
    public Map<String, Integer> getEnemies() {
        return enemies;
    }

    /** bosses.yml id, or {@code null} if this dungeon has no boss. */
    public String getBossId() {
        return bossId;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }
}
