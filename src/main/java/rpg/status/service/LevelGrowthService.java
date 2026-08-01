package rpg.status.service;

import rpg.status.config.StatusGrowthConfig;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;

/**
 * Turns a character level into a base {@link StatSheet}. HP/SP/ATK/DEF grow exponentially
 * ({@code base * growthRate^(level-1)}, mirroring {@link rpg.monster.config.MonsterLevelScalingConfig}
 * via the shared {@code stat-scaling.growth-rate}); CRT/CRT_DMG stay fixed at their base value
 * regardless of level; the remaining stats keep the flat {@code base + per-level * (level-1)}
 * growth configured in {@code config.yml: status.growth}.
 */
public final class LevelGrowthService {

    private final StatusGrowthConfig growthConfig;

    public LevelGrowthService(StatusGrowthConfig growthConfig) {
        this.growthConfig = growthConfig;
    }

    public StatSheet baseStatsForLevel(int level) {
        StatSheet sheet = StatSheet.empty();
        for (StatType type : StatType.values()) {
            double base = growthConfig.getBaseValue(type);
            double value;
            if (growthConfig.isExponential(type)) {
                value = base * Math.pow(growthConfig.getGrowthRate(type), Math.max(0, level - 1));
            } else if (growthConfig.isFixed(type)) {
                value = base;
            } else {
                double perLevel = growthConfig.getPerLevel(type);
                value = base + perLevel * Math.max(0, level - 1);
            }
            sheet.set(type, value);
        }
        return sheet;
    }
}
