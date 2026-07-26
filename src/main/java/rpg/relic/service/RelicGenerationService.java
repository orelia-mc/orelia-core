package rpg.relic.service;

import org.bukkit.inventory.ItemStack;
import rpg.accessory.model.AccessoryType;
import rpg.item.model.ElementType;
import rpg.relic.config.RelicConfig;
import rpg.relic.model.RelicInstance;
import rpg.relic.model.RelicLine;
import rpg.relic.model.RelicStatType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Rolls a brand-new relic (level 0) for a dungeon boss kill: a random part, a random main stat
 * from that part's {@code relics.yml} pool, and a handful of initial substats (see
 * {@link #rollInitialSubstats}) so a freshly-dropped relic doesn't read as a blank slate - the
 * "選べる厳選" differentiator (see {@code RelicUpgradeService}) is about *which* stat grows on
 * each upgrade, not about starting from zero.
 */
public final class RelicGenerationService {

    private static final ElementType[] ELEMENTS = {ElementType.FIRE, ElementType.WATER, ElementType.EARTH,
            ElementType.WIND, ElementType.LIGHT, ElementType.DARK};

    private final RelicConfig config;
    private final RelicFactory factory;
    private final Random random = new Random();

    public RelicGenerationService(RelicConfig config, RelicFactory factory) {
        this.config = config;
        this.factory = factory;
    }

    /** Empty if {@code relics.yml} has no parts configured, or the rolled part has an empty main-stat pool. */
    public Optional<ItemStack> generate(String sourceDungeonId) {
        List<AccessoryType> parts = config.getParts();
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        AccessoryType part = parts.get(random.nextInt(parts.size()));
        List<RelicStatType> pool = config.mainStatPoolFor(part);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        RelicStatType mainType = pool.get(random.nextInt(pool.size()));
        ElementType element = mainType == RelicStatType.ELEMENTAL_DMG_PERCENT ? randomElement() : ElementType.NONE;
        double value = config.valueRangeFor(mainType).map(range -> range.roll(random)).orElse(0.0);
        RelicLine mainStat = new RelicLine(mainType, element, round1(value));
        RelicInstance instance = new RelicInstance(UUID.randomUUID(), part, mainStat, rollInitialSubstats(mainType), 0, sourceDungeonId);
        return Optional.of(factory.build(instance));
    }

    /**
     * Picks {@link RelicConfig#getInitialSubstatCountMin}-{@link RelicConfig#getInitialSubstatCountMax}
     * distinct substat types (excluding the main stat's own type and {@code ELEMENTAL_DMG_PERCENT},
     * same exclusions {@code RelicUpgradeService#availableChoices} uses) and rolls each one's
     * starting value from {@link RelicConfig#getSubstatUpgradeRange} - the same magnitude a
     * player would get for "adding a new line" via an upgrade, just applied immediately.
     */
    private List<RelicLine> rollInitialSubstats(RelicStatType mainType) {
        List<RelicStatType> eligible = new ArrayList<>(Arrays.stream(RelicStatType.values())
                .filter(type -> type != RelicStatType.ELEMENTAL_DMG_PERCENT)
                .filter(type -> type != mainType)
                .toList());
        Collections.shuffle(eligible, random);
        int min = Math.min(config.getInitialSubstatCountMin(), eligible.size());
        int max = Math.min(config.getInitialSubstatCountMax(), eligible.size());
        int count = min + (max > min ? random.nextInt(max - min + 1) : 0);
        List<RelicLine> substats = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            substats.add(new RelicLine(eligible.get(i), ElementType.NONE, round1(config.getSubstatUpgradeRange().roll(random))));
        }
        return substats;
    }

    private ElementType randomElement() {
        return ELEMENTS[random.nextInt(ELEMENTS.length)];
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
