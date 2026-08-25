package rpg.extra.pet.model;

import rpg.core.player.PlayerDataComponent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player pet growth state, tracked independently per pet species id (so a player's choice
 * of which pet to main matters - leveling one species doesn't level the others). Unseen species
 * default to level 1 / 0 experience, matching a freshly-unlocked pet that's never been summoned.
 */
public final class PetGrowthComponent implements PlayerDataComponent {

    private final UUID owner;
    private final Map<String, Integer> levels;
    private final Map<String, Long> experience;

    public PetGrowthComponent(UUID owner, Map<String, Integer> levels, Map<String, Long> experience) {
        this.owner = owner;
        this.levels = new ConcurrentHashMap<>(levels);
        this.experience = new ConcurrentHashMap<>(experience);
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public int getLevel(String petId) {
        return levels.getOrDefault(petId, 1);
    }

    public long getExperience(String petId) {
        return experience.getOrDefault(petId, 0L);
    }

    public void setLevel(String petId, int level) {
        levels.put(petId, level);
    }

    public void setExperience(String petId, long amount) {
        experience.put(petId, amount);
    }

    /** Every species id this component has any recorded progress for - used when persisting. */
    public Map<String, Integer> getLevels() {
        return Map.copyOf(levels);
    }
}
