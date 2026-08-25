package rpg.item.service;

import org.bukkit.inventory.ItemStack;
import rpg.accessory.service.AccessoryIdentityService;
import rpg.relic.service.RelicIdentityService;

/**
 * Whether an {@link ItemStack} is allowed to change hands via Auction/Trade (SOW follow-up:
 * both previously accepted any non-empty stack, including plain vanilla items and the
 * {@code player_info_item}-tagged Nether Star menu item - see {@code rpg.world.playerinfo}). A
 * thin allow-list facade over the three per-category identity services rather than a new
 * identity system of its own: an item is tradeable exactly when it's a real Orelia weapon,
 * accessory, or relic - everything else (vanilla items, menu items, any future item category
 * this hasn't been extended for yet) is rejected by default rather than allowed by default,
 * matching the "positive allow-list" the report explicitly asked for over trying to enumerate
 * every kind of non-tradeable item instead.
 */
public final class TradeableItemService {

    private final WeaponIdentityService weaponIdentityService;
    private final AccessoryIdentityService accessoryIdentityService;
    private final RelicIdentityService relicIdentityService;

    public TradeableItemService(WeaponIdentityService weaponIdentityService, AccessoryIdentityService accessoryIdentityService,
                                 RelicIdentityService relicIdentityService) {
        this.weaponIdentityService = weaponIdentityService;
        this.accessoryIdentityService = accessoryIdentityService;
        this.relicIdentityService = relicIdentityService;
    }

    public boolean isTradeable(ItemStack stack) {
        return weaponIdentityService.idOf(stack).isPresent()
                || accessoryIdentityService.idOf(stack).isPresent()
                || relicIdentityService.isRelic(stack);
    }
}
