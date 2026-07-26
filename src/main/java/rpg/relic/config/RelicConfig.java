package rpg.relic.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.accessory.model.AccessoryType;
import rpg.item.model.ElementType;
import rpg.relic.model.RelicLine;
import rpg.relic.model.RelicStatType;
import rpg.status.model.ModifierType;
import rpg.status.model.StatType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads {@code relics.yml}: per-part main-stat pools, main-stat value ranges, upgrade cost, dungeon set bonuses, and fixed shop relics. */
public final class RelicConfig {

    public record RelicValueRange(double min, double max) {
        public double roll(java.util.Random random) {
            return min + random.nextDouble() * (max - min);
        }
    }

    public record DungeonSetBonus(StatType stat, ModifierType modifier, double value) {
    }

    /** A fixed, non-rolled relic recipe sold by shops - see {@code rpg.relic.service.RelicShopService}. */
    public record ShopRelicDefinition(AccessoryType part, RelicLine mainStat, List<RelicLine> substats) {
    }

    private double upgradeCostBase = 500;
    private double upgradeCostPerLevel = 300;
    private double substatUpgradeMin = 1.0;
    private double substatUpgradeMax = 2.0;
    private int initialSubstatCountMin = 3;
    private int initialSubstatCountMax = 4;
    private Map<AccessoryType, List<RelicStatType>> mainStatPools = new EnumMap<>(AccessoryType.class);
    private Map<RelicStatType, RelicValueRange> mainStatValueRanges = new EnumMap<>(RelicStatType.class);
    private Map<String, DungeonSetBonus> dungeonSetBonuses = new LinkedHashMap<>();
    private Map<String, ShopRelicDefinition> shopRelics = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        this.upgradeCostBase = config.getDouble("upgrade-cost-base", 500);
        this.upgradeCostPerLevel = config.getDouble("upgrade-cost-per-level", 300);
        this.substatUpgradeMin = config.getDouble("substat-upgrade-min", 1.0);
        this.substatUpgradeMax = Math.max(substatUpgradeMin, config.getDouble("substat-upgrade-max", 2.0));
        this.initialSubstatCountMin = Math.max(0, config.getInt("initial-substat-count-min", 3));
        this.initialSubstatCountMax = Math.max(initialSubstatCountMin, config.getInt("initial-substat-count-max", 4));
        this.mainStatPools = loadMainStatPools(config.getConfigurationSection("parts"));
        this.mainStatValueRanges = loadValueRanges(config.getConfigurationSection("main-stat-value-ranges"));
        this.dungeonSetBonuses = loadDungeonSetBonuses(config.getConfigurationSection("dungeon-set-bonuses"));
        this.shopRelics = loadShopRelics(config.getConfigurationSection("shop-relics"));
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

    private Map<String, ShopRelicDefinition> loadShopRelics(ConfigurationSection section) {
        Map<String, ShopRelicDefinition> relics = new LinkedHashMap<>();
        if (section == null) {
            return relics;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection relicSection = section.getConfigurationSection(id);
            if (relicSection == null) {
                continue;
            }
            try {
                AccessoryType part = AccessoryType.valueOf(relicSection.getString("part", "").trim().toUpperCase());
                RelicLine mainStat = readLine(relicSection.getConfigurationSection("main-stat"));
                List<RelicLine> substats = new ArrayList<>();
                for (Map<?, ?> raw : relicSection.getMapList("substats")) {
                    substats.add(readLine(raw));
                }
                relics.put(id, new ShopRelicDefinition(part, mainStat, substats));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // Malformed entry (unknown part/stat, missing main-stat) - skip rather than fail closed.
            }
        }
        return relics;
    }

    private RelicLine readLine(ConfigurationSection section) {
        RelicStatType type = RelicStatType.valueOf(section.getString("stat", "").trim().toUpperCase());
        ElementType element = ElementType.valueOf(section.getString("element", "NONE").trim().toUpperCase());
        return new RelicLine(type, element, section.getDouble("value", 0));
    }

    private RelicLine readLine(Map<?, ?> raw) {
        RelicStatType type = RelicStatType.valueOf(String.valueOf(raw.get("stat")).trim().toUpperCase());
        ElementType element = raw.containsKey("element")
                ? ElementType.valueOf(String.valueOf(raw.get("element")).trim().toUpperCase())
                : ElementType.NONE;
        double value = raw.get("value") instanceof Number number ? number.doubleValue() : 0;
        return new RelicLine(type, element, value);
    }

    public List<String> getShopRelicIds() {
        return List.copyOf(shopRelics.keySet());
    }

    public Optional<ShopRelicDefinition> shopRelicFor(String id) {
        return Optional.ofNullable(shopRelics.get(id));
    }

    public double getUpgradeCostBase() {
        return upgradeCostBase;
    }

    public double getUpgradeCostPerLevel() {
        return upgradeCostPerLevel;
    }

    /** How much a substat's value grows per pick - both when a brand-new relic rolls its initial lines and when {@code RelicUpgradeService} grows/adds one. */
    public RelicValueRange getSubstatUpgradeRange() {
        return new RelicValueRange(substatUpgradeMin, substatUpgradeMax);
    }

    /** A freshly-generated relic rolls this many initial substats (see {@code RelicGenerationService}) rather than starting blank. */
    public int getInitialSubstatCountMin() {
        return initialSubstatCountMin;
    }

    public int getInitialSubstatCountMax() {
        return initialSubstatCountMax;
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
