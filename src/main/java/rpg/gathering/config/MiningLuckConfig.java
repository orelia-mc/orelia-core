package rpg.gathering.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Bonus mining drop chance ({@code gathering.yml: mining-luck}). A pickaxe's own
 * {@code luck-level} (see {@code WeaponData#getLuckLevel}) rolls this chance once per level
 * per ore block broken.
 */
public final class MiningLuckConfig {

    private double bonusChancePercent;

    public MiningLuckConfig() {
        this(15.0);
    }

    public MiningLuckConfig(double bonusChancePercent) {
        this.bonusChancePercent = bonusChancePercent;
    }

    public void load(YamlConfiguration config) {
        this.bonusChancePercent = config.getDouble("mining-luck.bonus-chance-per-level", 15.0);
    }

    public double getBonusChancePercent() {
        return bonusChancePercent;
    }
}
