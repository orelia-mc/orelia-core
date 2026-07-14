package rpg.core.module;

import rpg.core.OreliaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Owns the registration order and lifecycle of every {@link RpgModule}.
 * Modules are enabled in registration order and disabled in reverse order so that
 * dependencies registered earlier (e.g. status before item) stay valid while
 * later modules shut down.
 */
public final class ModuleManager {

    private final OreliaPlugin plugin;
    private final List<RpgModule> registrationOrder = new ArrayList<>();
    private final Map<Class<? extends RpgModule>, RpgModule> byType = new LinkedHashMap<>();

    public ModuleManager(OreliaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(RpgModule module) {
        registrationOrder.add(module);
        byType.put(module.getClass(), module);
    }

    public void enableAll() {
        for (RpgModule module : registrationOrder) {
            try {
                module.onEnable(plugin);
                plugin.getLogger().info("Module enabled: " + module.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + module.getName(), e);
            }
        }
    }

    public void disableAll() {
        List<RpgModule> reversed = new ArrayList<>(registrationOrder);
        Collections.reverse(reversed);
        for (RpgModule module : reversed) {
            try {
                module.onDisable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable module: " + module.getName(), e);
            }
        }
    }

    public void reloadAll() {
        for (RpgModule module : registrationOrder) {
            try {
                module.onReload();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reload module: " + module.getName(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends RpgModule> Optional<T> get(Class<T> type) {
        return Optional.ofNullable((T) byType.get(type));
    }
}
