package rpg.monster.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code config.yml: monster-level-scaling.*} - how much a spawn point's optional
 * target level scales a spawned monster's {@code monsters.yml} hp/attack-power/defense from
 * their template values (see {@link rpg.monster.service.MonsterSpawnService}). Exponential
 * per-level growth, not linear: {@code scaled = base * growth^(targetLevel - 1)}, where
 * {@code hp-factor}/{@code attack-factor}/{@code defense-factor} are the growth multiplier
 * itself (e.g. {@code 1.1} = +10% per level, compounding), not an additive "extra percent".
 */
public final class MonsterLevelScalingConfig {

    private double hpFactor;
    private double attackFactor;
    private double defenseFactor;

    public MonsterLevelScalingConfig() {
        this(1.12, 1.10, 1.08);
    }

    MonsterLevelScalingConfig(double hpFactor, double attackFactor, double defenseFactor) {
        this.hpFactor = hpFactor;
        this.attackFactor = attackFactor;
        this.defenseFactor = defenseFactor;
    }

    public void load(YamlConfiguration config) {
        hpFactor = config.getDouble("monster-level-scaling.hp-factor", 1.12);
        attackFactor = config.getDouble("monster-level-scaling.attack-factor", 1.10);
        defenseFactor = config.getDouble("monster-level-scaling.defense-factor", 1.08);
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
