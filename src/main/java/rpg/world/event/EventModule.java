package rpg.world.event;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.util.ColorUtil;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.world.event.model.GameEventData;
import rpg.world.event.repository.EventRepository;
import rpg.world.event.service.EventScheduleService;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Event module: seasonal/world/limited-time events (events.yml) with a recurring or
 * one-off active window, and a broadcast the moment one starts.
 */
public final class EventModule implements RpgModule {

    private final EventRepository repository = new EventRepository();
    private EventScheduleService scheduleService;
    private Set<String> lastAnnouncedActive = new HashSet<>();
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "event";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        reloadEvents();
        this.scheduleService = new EventScheduleService(repository);

        // Checking every 5 minutes is plenty for month/day-granularity event windows.
        plugin.getSchedulerService().runTimer(this::checkForNewlyActiveEvents, 20L * 60, 20L * 60 * 5);
    }

    private void checkForNewlyActiveEvents() {
        Set<String> currentlyActive = scheduleService.getActiveEvents().stream()
                .map(GameEventData::getId).collect(Collectors.toSet());
        for (GameEventData event : scheduleService.getActiveEvents()) {
            if (!lastAnnouncedActive.contains(event.getId()) && event.getAnnounceMessage() != null && !event.getAnnounceMessage().isBlank()) {
                Bukkit.broadcast(net.kyori.adventure.text.Component.text(ColorUtil.colorize(event.getAnnounceMessage())));
            }
        }
        lastAnnouncedActive = currentlyActive;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadEvents();
    }

    private void reloadEvents() {
        plugin.getConfigManager().register("events.yml");
        YamlConfiguration config = plugin.getConfigManager().get("events.yml").get();
        repository.load(config);
    }

    public EventScheduleService getScheduleService() {
        return scheduleService;
    }
}
