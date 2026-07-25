package rpg.relic.service;

import org.bukkit.inventory.ItemStack;
import rpg.accessory.model.AccessoryType;
import rpg.item.model.ElementType;
import rpg.relic.config.RelicConfig;
import rpg.relic.model.RelicInstance;
import rpg.relic.model.RelicLine;
import rpg.relic.model.RelicStatType;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Rolls a brand-new relic (level 0, no substats yet) for a dungeon boss kill: a random part,
 * then a random main stat from that part's {@code relics.yml} pool. Substats are earned later
 * one at a time via {@code RelicUpgradeService} - see {@code Part I-4} of the relic design for
 * why they start empty (the "choose your own substats" differentiator needs a blank slate).
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
        RelicInstance instance = new RelicInstance(UUID.randomUUID(), part, mainStat, List.of(), 0, sourceDungeonId);
        return Optional.of(factory.build(instance));
    }

    private ElementType randomElement() {
        return ELEMENTS[random.nextInt(ELEMENTS.length)];
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
