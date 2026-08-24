package rpg.world.event.service;

import rpg.world.event.model.GameEventData;
import rpg.world.event.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Answers "which events are active right now" (SOW EventModule). Other world/core modules
 * can multiply rewards by {@link GameEventData#getBonusExpMultiplier()}/
 * {@link GameEventData#getBonusMoneyMultiplier()} for any currently-active event as a
 * follow-up integration; this service only exposes the schedule itself.
 */
public final class EventScheduleService {

    private final EventRepository repository;

    public EventScheduleService(EventRepository repository) {
        this.repository = repository;
    }

    public List<GameEventData> getActiveEvents() {
        LocalDateTime now = LocalDateTime.now();
        return repository.getAll().values().stream()
                .filter(event -> event.isActiveAt(now))
                .collect(Collectors.toList());
    }

    public double getBonusExpMultiplier() {
        return getActiveEvents().stream().mapToDouble(GameEventData::getBonusExpMultiplier).reduce(1.0, (a, b) -> a * b);
    }

    public double getBonusMoneyMultiplier() {
        return getActiveEvents().stream().mapToDouble(GameEventData::getBonusMoneyMultiplier).reduce(1.0, (a, b) -> a * b);
    }

    public Optional<GameEventData> findById(String id) {
        return repository.getAll().values().stream().filter(event -> event.getId().equals(id)).findFirst();
    }
}
