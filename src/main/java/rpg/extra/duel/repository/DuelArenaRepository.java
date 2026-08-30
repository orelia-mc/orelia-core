package rpg.extra.duel.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.config.ConfigFile;
import rpg.extra.duel.model.DuelArena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config-driven (duels.yml) storage for the flat arena list - mirrors
 * rpg.dungeon.repository.DungeonRepository's own arenas: parsing, but with no dungeon-id parent
 * key since a duel arena isn't owned by any other content entity.
 */
public final class DuelArenaRepository {

    private List<DuelArena> arenas = new ArrayList<>();

    public void load(YamlConfiguration config) {
        List<DuelArena> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                loaded.add(new DuelArena(
                        entry.getString("world", "world"),
                        entry.getDouble("x"),
                        entry.getDouble("y"),
                        entry.getDouble("z"),
                        (float) entry.getDouble("yaw", 0.0),
                        (float) entry.getDouble("pitch", 0.0)));
            }
        }
        this.arenas = loaded;
    }

    public List<DuelArena> getAll() {
        return List.copyOf(arenas);
    }

    /** Replaces the in-memory arena list only - call {@link #save} separately to persist to disk. */
    public void replace(List<DuelArena> updated) {
        this.arenas = new ArrayList<>(updated);
    }

    /** Writes the current in-memory arena list back to {@code file}, keyed by 0-based index. */
    public void save(ConfigFile file) {
        YamlConfiguration config = file.get();
        Map<String, Object> raw = new LinkedHashMap<>();
        for (int i = 0; i < arenas.size(); i++) {
            DuelArena arena = arenas.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", arena.world());
            entry.put("x", arena.x());
            entry.put("y", arena.y());
            entry.put("z", arena.z());
            entry.put("yaw", arena.yaw());
            entry.put("pitch", arena.pitch());
            raw.put(String.valueOf(i), entry);
        }
        config.set("arenas", raw);
        file.save();
    }
}
