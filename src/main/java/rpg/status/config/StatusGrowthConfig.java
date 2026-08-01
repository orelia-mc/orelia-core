package rpg.status.config;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.status.model.StatType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code config.yml: status.growth.<STAT>.base / .per-level} into lookup tables for
 * {@link rpg.status.service.LevelGrowthService}. HP/SP/ATK/DEF ({@link #EXPONENTIAL_STATS})
 * grow exponentially instead, via the shared {@code stat-scaling.growth-rate.<STAT>} also read
 * by {@link rpg.monster.config.MonsterLevelScalingConfig} - see {@link #getGrowthRate}. CRT/
 * CRT_DMG ({@link #FIXED_STATS}) never grow with level at all - {@code per-level} is loaded but
 * deliberately ignored by {@link #isFixed}'s callers, so a stray non-zero value left in an
 * existing config.yml can't creep crit stats up.
 */
public final class StatusGrowthConfig {

    /** Stats scaled by {@code base * growthRate^(level-1)} rather than flat per-level growth. */
    private static final Set<StatType> EXPONENTIAL_STATS =
            EnumSet.of(StatType.HP, StatType.SP, StatType.ATK, StatType.DEF);

    /** Stats that stay at their base value regardless of level - per-level growth never applies. */
    private static final Set<StatType> FIXED_STATS = EnumSet.of(StatType.CRT, StatType.CRT_DMG);

    private final Map<StatType, Double> baseValues = new EnumMap<>(StatType.class);
    private final Map<StatType, Double> perLevel = new EnumMap<>(StatType.class);
    private final Map<StatType, Double> growthRate = new EnumMap<>(StatType.class);

    public void load(YamlConfiguration config) {
        for (StatType type : StatType.values()) {
            String path = "status.growth." + type.name();
            baseValues.put(type, config.getDouble(path + ".base", defaultBase(type)));
            perLevel.put(type, config.getDouble(path + ".per-level", defaultPerLevel(type)));
        }
        for (StatType type : EXPONENTIAL_STATS) {
            growthRate.put(type, config.getDouble("stat-scaling.growth-rate." + type.name(), defaultGrowthRate(type)));
        }
    }

    public double getBaseValue(StatType type) {
        return baseValues.getOrDefault(type, 0.0);
    }

    public double getPerLevel(StatType type) {
        return perLevel.getOrDefault(type, 0.0);
    }

    public boolean isExponential(StatType type) {
        return EXPONENTIAL_STATS.contains(type);
    }

    public boolean isFixed(StatType type) {
        return FIXED_STATS.contains(type);
    }

    public double getGrowthRate(StatType type) {
        return growthRate.getOrDefault(type, 1.0);
    }

    private static double defaultGrowthRate(StatType type) {
        return switch (type) {
            case HP -> 1.045;
            case SP -> 1.04;
            case ATK -> 1.035;
            case DEF -> 1.03;
            default -> 1.0;
        };
    }

    private static double defaultBase(StatType type) {
        return switch (type) {
            case HP -> 100.0;
            case SP -> 50.0;
            case CRT -> 10.0;
            case CRT_DMG -> 50.0;
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
