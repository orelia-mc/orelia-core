package rpg.dungeon.model;

import rpg.core.player.PlayerDataComponent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player dungeon unlock log: which dungeons this player has discovered by right-clicking
 * their trigger block, and when. Unlocking is per-player and permanent (no re-locking) -
 * mirrors {@code rpg.quest.model.PlayerQuestComponent}'s completed-quest tracking shape.
 */
public final class PlayerDungeonComponent implements PlayerDataComponent {

    private final UUID owner;
    private final Map<String, Instant> unlockedDungeons;

    public PlayerDungeonComponent(UUID owner, Map<String, Instant> unlockedDungeons) {
        this.owner = owner;
        this.unlockedDungeons = new HashMap<>(unlockedDungeons);
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public boolean isUnlocked(String dungeonId) {
        return unlockedDungeons.containsKey(dungeonId);
    }

    public void unlock(String dungeonId) {
        unlockedDungeons.putIfAbsent(dungeonId, Instant.now());
    }

    public Set<String> getUnlockedDungeonIds() {
        return Set.copyOf(unlockedDungeons.keySet());
    }

    public Map<String, Instant> getUnlockedDungeonsWithTimestamps() {
        return Map.copyOf(unlockedDungeons);
    }
}
