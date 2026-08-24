package rpg.dungeon.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.dungeon.model.DungeonArena;
import rpg.dungeon.model.DungeonData;
import rpg.dungeon.model.DungeonType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link DungeonData}, rebuilt from {@code dungeons.yml}.
 */
public final class DungeonRepository {

    private Map<String, DungeonData> dungeons = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, DungeonData> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("dungeons");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection dungeonSection = section.getConfigurationSection(id);
                if (dungeonSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, dungeonSection));
            }
        }
        this.dungeons = loaded;
    }

    private DungeonData parse(String id, ConfigurationSection section) {
        return new DungeonData(
                id,
                section.getString("name", id),
                DungeonType.valueOf(section.getString("type", "NORMAL").trim().toUpperCase()),
                section.getInt("min-party-size", 1),
                section.getInt("max-party-size", 1),
                parseArenas(section),
                section.getLong("reward-exp", 0),
                section.getDouble("reward-money", 0),
                parseEnemies(section.getConfigurationSection("enemies")),
                section.getString("boss-id", null),
                section.getInt("time-limit-seconds", 300));
    }

    /**
     * Reads the {@code arenas} list. Falls back to a single arena synthesized from the legacy
     * scalar {@code world}/{@code x}/{@code y}/{@code z} keys when {@code arenas} is absent, so a
     * dungeon an admin hand-customized before the multi-arena feature shipped keeps working
     * (single concurrent run) instead of failing to load.
     */
    private List<DungeonArena> parseArenas(ConfigurationSection section) {
        List<Map<?, ?>> rawArenas = section.getMapList("arenas");
        if (rawArenas.isEmpty()) {
            return List.of(new DungeonArena(
                    section.getString("world", "world"),
                    section.getDouble("x", 0),
                    section.getDouble("y", 64),
                    section.getDouble("z", 0)));
        }
        List<DungeonArena> arenas = new ArrayList<>();
        for (Map<?, ?> raw : rawArenas) {
            Object world = raw.get("world");
            arenas.add(new DungeonArena(
                    world == null ? "world" : String.valueOf(world),
                    toDouble(raw.get("x")),
                    toDouble(raw.get("y")),
                    toDouble(raw.get("z"))));
        }
        return arenas;
    }

    private double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private Map<String, Integer> parseEnemies(ConfigurationSection enemiesSection) {
        Map<String, Integer> enemies = new LinkedHashMap<>();
        if (enemiesSection != null) {
            for (String monsterId : enemiesSection.getKeys(false)) {
                enemies.put(monsterId, enemiesSection.getInt(monsterId));
            }
        }
        return enemies;
    }

    public Optional<DungeonData> findById(String id) {
        return Optional.ofNullable(dungeons.get(id));
    }

    public Map<String, DungeonData> getAll() {
        return Map.copyOf(dungeons);
    }
}
