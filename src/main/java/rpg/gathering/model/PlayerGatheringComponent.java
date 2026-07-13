package rpg.gathering.model;

import rpg.core.player.PlayerDataComponent;

import java.util.UUID;

/**
 * Per-player gathering level and experience, shared by mining, woodcutting, and farming
 * (SOW 3.3 - one leveling/radius system feeding all three activities).
 */
public final class PlayerGatheringComponent implements PlayerDataComponent {

    private final UUID owner;
    private int level;
    private long experience;

    public PlayerGatheringComponent(UUID owner, int level, long experience) {
        this.owner = owner;
        this.level = level;
        this.experience = experience;
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public int getLevel() {
        return level;
    }

    public long getExperience() {
        return experience;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setExperience(long experience) {
        this.experience = experience;
    }
}
