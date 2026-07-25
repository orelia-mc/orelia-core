package rpg.relic.service;

import org.bukkit.inventory.ItemStack;
import rpg.relic.config.RelicConfig;
import rpg.relic.model.RelicInstance;

import java.util.Optional;
import java.util.UUID;

/**
 * Builds fixed, non-rolled relics sold by shops (see {@code relics.yml}'s {@code shop-relics:}
 * section) - intentionally weaker than a boss-dropped relic (fixed stats, no "選べる厳選"
 * upgrade path since it's already at max level 15) but the same underlying item type, reusing
 * {@link RelicFactory#build}. {@code sourceDungeonId} is fixed to {@code "shop"}, which never
 * matches any {@code relics.yml} dungeon-set-bonus key, so shop relics never contribute to a
 * dungeon set bonus.
 */
public final class RelicShopService {

    private static final String SOURCE_ID = "shop";
    private static final int MAX_LEVEL = 15;

    private final RelicConfig relicConfig;
    private final RelicFactory relicFactory;

    public RelicShopService(RelicConfig relicConfig, RelicFactory relicFactory) {
        this.relicConfig = relicConfig;
        this.relicFactory = relicFactory;
    }

    /** Empty if {@code shopRelicId} has no matching entry under {@code relics.yml}'s {@code shop-relics:}. */
    public Optional<ItemStack> build(String shopRelicId) {
        return relicConfig.shopRelicFor(shopRelicId).map(def -> {
            RelicInstance instance = new RelicInstance(
                    UUID.randomUUID(), def.part(), def.mainStat(), def.substats(), MAX_LEVEL, SOURCE_ID);
            return relicFactory.build(instance);
        });
    }
}
