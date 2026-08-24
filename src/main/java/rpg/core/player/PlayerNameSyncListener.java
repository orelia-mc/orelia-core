package rpg.core.player;

import java.util.UUID;

/**
 * Hook for a module that wants to know a player's name as soon as {@link PlayerDataManager}
 * learns it (on join), without widening {@link PlayerDataComponentLoader}'s contract to carry
 * a name through every module's load/save path. Registered via
 * {@link PlayerDataManager#registerNameSyncListener}.
 */
@FunctionalInterface
public interface PlayerNameSyncListener {
    void onNameKnown(UUID uuid, String name);
}
