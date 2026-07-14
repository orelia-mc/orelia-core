package rpg.core.player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime container for one online player's cross-module data. Core only manages the
 * UUID/name and the component map; the meaning of each component belongs to the
 * module that registered its {@link PlayerDataComponentLoader}.
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile String name;
    private final Map<Class<? extends PlayerDataComponent>, PlayerDataComponent> components = new ConcurrentHashMap<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public <T extends PlayerDataComponent> void attach(Class<T> type, T component) {
        components.put(type, component);
    }

    @SuppressWarnings("unchecked")
    public <T extends PlayerDataComponent> Optional<T> component(Class<T> type) {
        return Optional.ofNullable((T) components.get(type));
    }

    /**
     * Convenience accessor for modules that can guarantee the component was attached
     * during load (i.e. their own loader is registered). Throws otherwise so a missing
     * registration fails loudly instead of causing silent null-stat bugs.
     */
    public <T extends PlayerDataComponent> T require(Class<T> type) {
        return component(type).orElseThrow(() ->
                new IllegalStateException("Player data component not attached: " + type.getSimpleName()));
    }

    Iterable<PlayerDataComponent> allComponents() {
        return components.values();
    }
}
