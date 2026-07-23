package rpg.gathering.model;

import rpg.core.player.PlayerDataComponent;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player gathering level and experience, tracked independently per
 * {@link GatherActionType} (mining/woodcutting/farming each level their own job -
 * SOW 3.3 update) rather than one level shared across all three.
 */
public final class PlayerGatheringComponent implements PlayerDataComponent {

    private final UUID owner;
    private final Map<GatherActionType, Integer> levels = new EnumMap<>(GatherActionType.class);
    private final Map<GatherActionType, Long> experience = new EnumMap<>(GatherActionType.class);

    public PlayerGatheringComponent(UUID owner, Map<GatherActionType, Integer> levels, Map<GatherActionType, Long> experience) {
        this.owner = owner;
        for (GatherActionType type : GatherActionType.values()) {
            this.levels.put(type, levels.getOrDefault(type, 1));
            this.experience.put(type, experience.getOrDefault(type, 0L));
        }
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public int getLevel(GatherActionType type) {
        return levels.get(type);
    }

    public long getExperience(GatherActionType type) {
        return experience.get(type);
    }

    public void setLevel(GatherActionType type, int level) {
        levels.put(type, level);
    }

    public void setExperience(GatherActionType type, long amount) {
        experience.put(type, amount);
    }
}
