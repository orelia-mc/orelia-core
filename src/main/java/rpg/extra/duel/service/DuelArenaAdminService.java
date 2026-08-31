package rpg.extra.duel.service;

import org.bukkit.Location;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.extra.duel.model.DuelArena;
import rpg.extra.duel.repository.DuelArenaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs /oladmin duelarena add|set|remove|list - mirrors rpg.dungeon.service.DungeonArenaAdminService
 * but flat (no dungeon-id parent, no "last arena" restriction - an empty arena list is a valid
 * "not configured yet" state for duels, unlike dungeons' legacy-scalar-fallback concern).
 */
public final class DuelArenaAdminService {

    private static final String DUELS_YML = "duels.yml";

    private final DuelArenaRepository repository;
    private final ConfigManager configManager;

    public DuelArenaAdminService(DuelArenaRepository repository, ConfigManager configManager) {
        this.repository = repository;
        this.configManager = configManager;
    }

    public DuelArena addArena(Location location) {
        DuelArena arena = fromLocation(location);
        List<DuelArena> arenas = new ArrayList<>(repository.getAll());
        arenas.add(arena);
        apply(arenas);
        return arena;
    }

    public enum SetResult { OK, INDEX_OUT_OF_RANGE }

    /** 1-based index, matching {@link #listArenas}'s numbering (same convention DungeonArenaAdminService uses). */
    public SetResult setArena(int index, Location location) {
        List<DuelArena> arenas = new ArrayList<>(repository.getAll());
        if (index < 1 || index > arenas.size()) {
            return SetResult.INDEX_OUT_OF_RANGE;
        }
        arenas.set(index - 1, fromLocation(location));
        apply(arenas);
        return SetResult.OK;
    }

    public enum RemoveResult { OK, INDEX_OUT_OF_RANGE }

    public RemoveResult removeArena(int index) {
        List<DuelArena> arenas = new ArrayList<>(repository.getAll());
        if (index < 1 || index > arenas.size()) {
            return RemoveResult.INDEX_OUT_OF_RANGE;
        }
        arenas.remove(index - 1);
        apply(arenas);
        return RemoveResult.OK;
    }

    public List<DuelArena> listArenas() {
        return repository.getAll();
    }

    private DuelArena fromLocation(Location location) {
        return new DuelArena(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    private void apply(List<DuelArena> arenas) {
        repository.replace(arenas);
        ConfigFile file = configManager.get(DUELS_YML);
        repository.save(file);
    }
}
