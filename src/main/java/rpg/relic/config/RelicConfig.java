package rpg.relic.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.accessory.model.AccessoryType;
import rpg.relic.model.RelicStatType;
import rpg.status.model.ModifierType;
import rpg.status.model.StatType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads {@code relics.yml}: per-part main-stat pools, main-stat value ranges, upgrade cost, and dungeon set bonuses. */
public final class RelicConfig {

    public record RelicValueRange(double min, double max) {
        public double roll(java.util.Random random) {
            return min + random.nextDouble() * (max - min);
        }
    }

    public record DungeonSetBonus(StatType stat, ModifierType modifier, double value) {
    }

    private double upgradeCostBase = 500;
    private double upgradeCostPerLevel = 300;
    private Map<AccessoryType, List<RelicStatType>> mainStatPools = new EnumMap<>(AccessoryType.class);
    private Map<RelicStatType, RelicValueRange> mainStatValueRanges = new EnumMap<>(RelicStatType.class);
    private Map<String, DungeonSetBonus> dungeonSetBonuses = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        this.upgradeCostBase = config.getDouble("upgrade-cost-base", 500);
        this.upgradeCostPerLevel = config.getDouble("upgrade-cost-per-level", 300);
        this.mainStatPools = loadMainStatPools(config.getConfigurationSection("parts"));
        this.mainStatValueRanges = loadValueRanges(config.getConfigurationSection("main-stat-value-ranges"));
        this.dungeonSetBonuses = loadDungeonSetBonuses(config.getConfigurationSection("dungeon-set-bonuses"));
    }

    private Map<AccessoryType, List<RelicStatType>> loadMainStatPools(ConfigurationSection section) {
        Map<AccessoryType, List<RelicStatType>> pools = new EnumMap<>(AccessoryType.class);
        if (section != null) {
            for (String partName : section.getKeys(false)) {
                AccessoryType part;
                try {
                    part = AccessoryType.valueOf(partName.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    continue; // unknown part in relics.yml - skip rather than fail closed
                }
                List<String> rawPool = section.getStringList(partName + ".main-stat-pool");
                List<RelicStatType> pool = new ArrayList<>();
                for (String raw : rawPool) {
                    try {
                        pool.add(RelicStatType.valueOf(raw.trim().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                pools.put(part, pool);
            }
        }
        return pools;
    }

    private Map<RelicStatType, RelicValueRange> loadValueRanges(ConfigurationSection section) {
        Map<RelicStatType, RelicValueRange> ranges = new EnumMap<>(RelicStatType.class);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    RelicStatType type = RelicStatType.valueOf(key.trim().toUpperCase());
                    ConfigurationSection rangeSection = section.getConfigurationSection(key);
                    if (rangeSection != null) {
                        ranges.put(type, new RelicValueRange(rangeSection.getDouble("min", 0), rangeSection.getDouble("max", 0)));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return ranges;
    }

    private Map<String, DungeonSetBonus> loadDungeonSetBonuses(ConfigurationSection section) {
        Map<String, DungeonSetBonus> bonuses = new LinkedHashMap<>();
        if (section != null) {
            for (String dungeonId : section.getKeys(false)) {
                ConfigurationSection bonusSection = section.getConfigurationSection(dungeonId);
                if (bonusSection == null) {
                    continue;
                }
                try {
                    StatType stat = StatType.valueOf(bonusSection.getString("stat", "").trim().toUpperCase());
                    ModifierType modifier = ModifierType.valueOf(bonusSection.getString("modifier", "FLAT").trim().toUpperCase());
                    bonuses.put(dungeonId, new DungeonSetBonus(stat, modifier, bonusSection.getDouble("value", 0)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return bonuses;
    }

    public double getUpgradeCostBase() {
        return upgradeCostBase;
    }

    public double getUpgradeCostPerLevel() {
        return upgradeCostPerLevel;
    }

    public List<AccessoryType> getParts() {
        return List.copyOf(mainStatPools.keySet());
    }

    public List<RelicStatType> mainStatPoolFor(AccessoryType part) {
        return mainStatPools.getOrDefault(part, List.of());
    }

    public Optional<RelicValueRange> valueRangeFor(RelicStatType type) {
        return Optional.ofNullable(mainStatValueRanges.get(type));
    }

    public Optional<DungeonSetBonus> setBonusFor(String dungeonId) {
        return Optional.ofNullable(dungeonSetBonuses.get(dungeonId));
    }

    public List<String> getDungeonIdsWithSetBonus() {
        return List.copyOf(dungeonSetBonuses.keySet());
    }
}
