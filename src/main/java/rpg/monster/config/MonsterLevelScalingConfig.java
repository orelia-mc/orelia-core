package rpg.monster.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code config.yml: stat-scaling.growth-rate.*} - how much a spawn point's optional
 * target level scales a spawned monster's {@code monsters.yml} hp/attack-power/defense from
 * their template values (see {@link rpg.monster.service.MonsterSpawnService}). Exponential
 * per-level growth, not linear: {@code scaled = base * growth^(targetLevel - 1)}, where the
 * HP/ATK/DEF growth rates are the growth multiplier itself (e.g. {@code 1.045} = +4.5% per
 * level, compounding), not an additive "extra percent". This {@code stat-scaling.growth-rate}
 * section is shared with player character growth ({@link rpg.status.service.LevelGrowthService}),
 * so a monster and a player at the same level grow at the same rate.
 */
public final class MonsterLevelScalingConfig {

    private double hpFactor;
    private double attackFactor;
    private double defenseFactor;

    public MonsterLevelScalingConfig() {
        this(1.045, 1.035, 1.03);
    }

    MonsterLevelScalingConfig(double hpFactor, double attackFactor, double defenseFactor) {
        this.hpFactor = hpFactor;
        this.attackFactor = attackFactor;
        this.defenseFactor = defenseFactor;
    }

    public void load(YamlConfiguration config) {
        hpFactor = config.getDouble("stat-scaling.growth-rate.HP", 1.045);
        attackFactor = config.getDouble("stat-scaling.growth-rate.ATK", 1.035);
        defenseFactor = config.getDouble("stat-scaling.growth-rate.DEF", 1.03);
    }

    /** {@code targetLevel == null} means "no scaling" - returns {@code baseHp} unchanged. */
    public double scaledHp(double baseHp, Integer targetLevel) {
        return scale(baseHp, hpFactor, targetLevel, 1.0);
    }

    public double scaledAttackPower(double baseAttackPower, Integer targetLevel) {
        return scale(baseAttackPower, attackFactor, targetLevel, 0.0);
    }

    public double scaledDefense(double baseDefense, Integer targetLevel) {
        return scale(baseDefense, defenseFactor, targetLevel, 0.0);
    }

    private double scale(double base, double growth, Integer targetLevel, double minimum) {
        if (targetLevel == null) {
            return base;
        }
        return Math.max(base * Math.pow(growth, targetLevel - 1), minimum);
    }
}
