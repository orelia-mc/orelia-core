package rpg.extra.mount.model;

import rpg.core.player.PlayerDataComponent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player mount growth state, tracked independently per mount species id - same shape as
 * {@code rpg.extra.pet.model.PetGrowthComponent}. Unseen species default to level 1 / 0
 * experience.
 */
public final class MountGrowthComponent implements PlayerDataComponent {

    private final UUID owner;
    private final Map<String, Integer> levels;
    private final Map<String, Long> experience;

    public MountGrowthComponent(UUID owner, Map<String, Integer> levels, Map<String, Long> experience) {
        this.owner = owner;
        this.levels = new ConcurrentHashMap<>(levels);
        this.experience = new ConcurrentHashMap<>(experience);
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public int getLevel(String mountId) {
        return levels.getOrDefault(mountId, 1);
    }

    public long getExperience(String mountId) {
        return experience.getOrDefault(mountId, 0L);
    }

    public void setLevel(String mountId, int level) {
        levels.put(mountId, level);
    }

    public void setExperience(String mountId, long amount) {
        experience.put(mountId, amount);
    }

    public Map<String, Integer> getLevels() {
        return Map.copyOf(levels);
    }
}
