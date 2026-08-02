package rpg.core.player;

import rpg.core.scheduler.SchedulerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the online-player cache and drives component load/save through every registered
 * {@link PlayerDataComponentLoader}. This is the only class that touches the raw
 * {@link PlayerData} map; modules interact through {@link #get(UUID)}.
 */
public final class PlayerDataManager {

    private final Logger logger;
    private final SchedulerService scheduler;
    private final Map<UUID, PlayerData> online = new ConcurrentHashMap<>();
    private final List<PlayerDataComponentLoader<?>> loaders = new ArrayList<>();
    private final List<PlayerNameSyncListener> nameSyncListeners = new ArrayList<>();

    public PlayerDataManager(Logger logger, SchedulerService scheduler) {
        this.logger = logger;
        this.scheduler = scheduler;
    }

    public void registerLoader(PlayerDataComponentLoader<?> loader) {
        loaders.add(loader);
    }

    /**
     * Registers a listener notified with (uuid, name) as soon as a player's name is known
     * on join - lets a module (e.g. status leaderboard) persist a name against its own table
     * without {@link PlayerDataComponentLoader#save} needing to carry one.
     */
    public void registerNameSyncListener(PlayerNameSyncListener listener) {
        nameSyncListeners.add(listener);
    }

    /**
     * Loads every registered component for the given player off the main thread, then
     * invokes {@code onLoaded} back on the main thread once the data is cached.
     */
    public void loadAsync(UUID uuid, String name, Runnable onLoaded) {
        scheduler.runAsync(() -> {
            PlayerData data = new PlayerData(uuid, name);
            for (PlayerDataComponentLoader<?> loader : loaders) {
                attachLoaded(data, loader, uuid);
            }
            for (PlayerNameSyncListener listener : nameSyncListeners) {
                try {
                    listener.onNameKnown(uuid, name);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to sync name for " + uuid, e);
                }
            }
            online.put(uuid, data);
            scheduler.runSync(onLoaded);
        });
    }

    private <T extends PlayerDataComponent> void attachLoaded(PlayerData data, PlayerDataComponentLoader<T> loader, UUID uuid) {
        try {
            data.attach(loader.type(), loader.loadOrCreate(uuid));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load player data component " + loader.type().getSimpleName() + " for " + uuid, e);
        }
    }

    /**
     * Saves every attached component for the given player off the main thread, then
     * evicts them from the cache.
     */
    public void saveAndUnloadAsync(UUID uuid) {
        PlayerData data = online.remove(uuid);
        if (data == null) {
            return;
        }
        scheduler.runAsync(() -> saveAll(data));
    }

    public void saveAllOnlineSync() {
        online.values().forEach(this::saveAll);
    }

    @SuppressWarnings("unchecked")
    private void saveAll(PlayerData data) {
        for (PlayerDataComponentLoader<?> loader : loaders) {
            PlayerDataComponent component = data.component((Class<PlayerDataComponent>) (Class<?>) loader.type()).orElse(null);
            if (component == null) {
                continue;
            }
            try {
                ((PlayerDataComponentLoader<PlayerDataComponent>) loader).save(component);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player data component " + loader.type().getSimpleName() + " for " + data.getUuid(), e);
            }
        }
    }

    public Optional<PlayerData> get(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }
}
