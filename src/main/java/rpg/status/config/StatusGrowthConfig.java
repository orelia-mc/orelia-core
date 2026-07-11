package rpg.status.config;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.status.model.StatType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Loads {@code config.yml: status.growth.<STAT>.base / .per-level} into lookup tables
 * for {@link rpg.status.service.LevelGrowthService}.
 */
public final class StatusGrowthConfig {

    private final Map<StatType, Double> baseValues = new EnumMap<>(StatType.class);
    private final Map<StatType, Double> perLevel = new EnumMap<>(StatType.class);

    public void load(YamlConfiguration config) {
        for (StatType type : StatType.values()) {
            String path = "status.growth." + type.name();
            baseValues.put(type, config.getDouble(path + ".base", defaultBase(type)));
            perLevel.put(type, config.getDouble(path + ".per-level", defaultPerLevel(type)));
        }
    }

    public double getBaseValue(StatType type) {
        return baseValues.getOrDefault(type, 0.0);
    }

    public double getPerLevel(StatType type) {
        return perLevel.getOrDefault(type, 0.0);
    }

    private static double defaultBase(StatType type) {
        return switch (type) {
            case HP -> 100.0;
            case SP -> 50.0;
            default -> 5.0;
        };
    }

    private static double defaultPerLevel(StatType type) {
        return switch (type) {
            case HP -> 10.0;
            case SP -> 5.0;
            default -> 1.0;
        };
    }
}
