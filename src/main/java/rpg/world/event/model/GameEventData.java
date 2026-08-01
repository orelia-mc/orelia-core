package rpg.world.event.model;

import java.time.LocalDateTime;
import java.time.MonthDay;

/**
 * A time-windowed event (SOW EventModule). {@code recurring} events (typical for
 * SEASONAL - Halloween, Christmas, ...) repeat every year on a month/day window;
 * non-recurring events use an explicit one-off start/end timestamp (typical for LIMITED).
 */
public final class GameEventData {

    private final String id;
    private final String name;
    private final GameEventType type;
    private final boolean recurring;
    private final MonthDay recurringStart;
    private final MonthDay recurringEnd;
    private final LocalDateTime oneOffStart;
    private final LocalDateTime oneOffEnd;
    private final double bonusExpMultiplier;
    private final double bonusMoneyMultiplier;
    private final String announceMessage;

    public GameEventData(String id, String name, GameEventType type, boolean recurring,
                          MonthDay recurringStart, MonthDay recurringEnd,
                          LocalDateTime oneOffStart, LocalDateTime oneOffEnd,
                          double bonusExpMultiplier, double bonusMoneyMultiplier, String announceMessage) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.recurring = recurring;
        this.recurringStart = recurringStart;
        this.recurringEnd = recurringEnd;
        this.oneOffStart = oneOffStart;
        this.oneOffEnd = oneOffEnd;
        this.bonusExpMultiplier = bonusExpMultiplier;
        this.bonusMoneyMultiplier = bonusMoneyMultiplier;
        this.announceMessage = announceMessage;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public GameEventType getType() {
        return type;
    }

    public boolean isActiveAt(LocalDateTime now) {
        if (recurring) {
            MonthDay today = MonthDay.from(now);
            if (recurringStart.isAfter(recurringEnd)) {
                // Window wraps the new year, e.g. Dec 20 - Jan 5.
                return !today.isBefore(recurringStart) || !today.isAfter(recurringEnd);
            }
            return !today.isBefore(recurringStart) && !today.isAfter(recurringEnd);
        }
        return oneOffStart != null && oneOffEnd != null && !now.isBefore(oneOffStart) && !now.isAfter(oneOffEnd);
    }

    public double getBonusExpMultiplier() {
        return bonusExpMultiplier;
    }

    public double getBonusMoneyMultiplier() {
        return bonusMoneyMultiplier;
    }

    public String getAnnounceMessage() {
        return announceMessage;
    }
}
