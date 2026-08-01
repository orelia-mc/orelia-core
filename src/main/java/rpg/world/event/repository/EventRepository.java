package rpg.world.event.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.world.event.model.GameEventData;
import rpg.world.event.model.GameEventType;

import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory registry of every {@link GameEventData}, rebuilt from {@code events.yml}.
 */
public final class EventRepository {

    private Map<String, GameEventData> events = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, GameEventData> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("events");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection eventSection = section.getConfigurationSection(id);
                if (eventSection == null) {
                    continue;
                }
                GameEventData parsed = parse(id, eventSection);
                if (parsed != null) {
                    loaded.put(id, parsed);
                }
            }
        }
        this.events = loaded;
    }

    private GameEventData parse(String id, ConfigurationSection section) {
        boolean recurring = section.getBoolean("recurring", true);
        try {
            if (recurring) {
                return new GameEventData(
                        id,
                        section.getString("name", id),
                        GameEventType.valueOf(section.getString("type", "SEASONAL").trim().toUpperCase()),
                        true,
                        MonthDay.parse(section.getString("start", "--01-01")),
                        MonthDay.parse(section.getString("end", "--01-01")),
                        null, null,
                        section.getDouble("bonus-exp-multiplier", 1.0),
                        section.getDouble("bonus-money-multiplier", 1.0),
                        section.getString("announce-message"));
            }
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            return new GameEventData(
                    id,
                    section.getString("name", id),
                    GameEventType.valueOf(section.getString("type", "LIMITED").trim().toUpperCase()),
                    false,
                    null, null,
                    LocalDateTime.parse(section.getString("start"), formatter),
                    LocalDateTime.parse(section.getString("end"), formatter),
                    section.getDouble("bonus-exp-multiplier", 1.0),
                    section.getDouble("bonus-money-multiplier", 1.0),
                    section.getString("announce-message"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public Map<String, GameEventData> getAll() {
        return Map.copyOf(events);
    }
}
