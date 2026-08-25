package rpg.dungeon.service;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.dungeon.model.DungeonArena;
import rpg.dungeon.model.DungeonData;
import rpg.dungeon.repository.DungeonRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backs {@code /oladmin dungeonarena add|remove|list}: lets an admin register/remove a
 * dungeon's physical entry point(s) from where they're standing instead of hand-editing
 * {@code dungeons.yml}'s {@code arenas:} list. Never creates or deletes the dungeon itself -
 * a dungeon's name/enemies/reward/boss-id stay hand-authored content, this service only owns
 * "where can this dungeon's runs be spawned".
 *
 * <p><b>Must always read arenas via {@link DungeonRepository#findById} (the already-resolved
 * in-memory list), never by re-parsing raw YAML.</b> {@code DungeonRepository#parseArenas}
 * synthesizes a single legacy arena from a dungeon's scalar {@code world}/{@code x}/{@code y}/
 * {@code z} keys when {@code arenas:} is absent - once any {@code arenas:} list is written to
 * disk, that fallback never triggers again on future loads, so building a new arena list from
 * raw YAML for an {@code arenas:}-less dungeon would silently drop its original entry point.
 * For the same reason, {@link #removeArena} refuses to remove a dungeon's last remaining arena
 * - an empty {@code arenas:} list isn't "no arenas", it's "fall back to the legacy scalar
 * fields again", which could resurrect a stale default location nobody intended.
 */
public final class DungeonArenaAdminService {

    private static final String DUNGEONS_YML = "dungeons.yml";

    private final DungeonRepository repository;
    private final ConfigManager configManager;

    public DungeonArenaAdminService(DungeonRepository repository, ConfigManager configManager) {
        this.repository = repository;
        this.configManager = configManager;
    }

    /** Appends a new arena at {@code location}. Empty if {@code dungeonId} isn't a defined dungeon. */
    public Optional<DungeonArena> addArena(String dungeonId, Location location) {
        DungeonData existing = repository.findById(dungeonId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        DungeonArena arena = new DungeonArena(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
        List<DungeonArena> arenas = new ArrayList<>(existing.getArenas());
        arenas.add(arena);
        applyArenas(dungeonId, existing, arenas);
        return Optional.of(arena);
    }

    public enum RemoveResult { OK, DUNGEON_NOT_FOUND, INDEX_OUT_OF_RANGE, LAST_ARENA }

    /**
     * Removes the arena at 1-based {@code index} (matching the numbering {@link #listArenas}
     * prints for admin use). {@link RemoveResult#LAST_ARENA} blocks removing a dungeon's only
     * remaining arena - see this class's own doc comment for why.
     */
    public RemoveResult removeArena(String dungeonId, int index) {
        DungeonData existing = repository.findById(dungeonId).orElse(null);
        if (existing == null) {
            return RemoveResult.DUNGEON_NOT_FOUND;
        }
        List<DungeonArena> arenas = new ArrayList<>(existing.getArenas());
        if (index < 1 || index > arenas.size()) {
            return RemoveResult.INDEX_OUT_OF_RANGE;
        }
        if (arenas.size() <= 1) {
            return RemoveResult.LAST_ARENA;
        }
        arenas.remove(index - 1);
        applyArenas(dungeonId, existing, arenas);
        return RemoveResult.OK;
    }

    public List<DungeonArena> listArenas(String dungeonId) {
        return repository.findById(dungeonId).map(DungeonData::getArenas).orElse(List.of());
    }

    private void applyArenas(String dungeonId, DungeonData existing, List<DungeonArena> arenas) {
        DungeonData updated = withArenas(existing, arenas);
        repository.replace(dungeonId, updated);
        writeArenas(dungeonId, arenas);
    }

    /** Rebuilds {@code existing} with only its arena list swapped - {@link DungeonData} is immutable with no builder anywhere in this codebase, so every field is re-passed as-is. */
    private DungeonData withArenas(DungeonData existing, List<DungeonArena> arenas) {
        return new DungeonData(existing.getId(), existing.getName(), existing.getType(),
                existing.getMinPartySize(), existing.getMaxPartySize(), arenas,
                existing.getRewardExp(), existing.getRewardMoney(), existing.getEnemies(),
                existing.getBossId(), existing.getTimeLimitSeconds());
    }

    private void writeArenas(String dungeonId, List<DungeonArena> arenas) {
        ConfigFile file = configManager.get(DUNGEONS_YML);
        YamlConfiguration config = file.get();
        List<Map<String, Object>> raw = new ArrayList<>();
        for (DungeonArena arena : arenas) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", arena.world());
            entry.put("x", arena.x());
            entry.put("y", arena.y());
            entry.put("z", arena.z());
            raw.add(entry);
        }
        config.set("dungeons." + dungeonId + ".arenas", raw);
        file.save();
    }
}
