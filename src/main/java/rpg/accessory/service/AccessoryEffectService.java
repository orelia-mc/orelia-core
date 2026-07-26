package rpg.accessory.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.accessory.model.AccessoryData;
import rpg.accessory.model.AccessoryType;
import rpg.accessory.model.PlayerAccessoryEquipmentComponent;
import rpg.core.player.PlayerDataManager;
import rpg.status.service.StatusService;

/**
 * Applies/removes the stat bonus of an equipped accessory to the status module, and
 * resyncs every slot (e.g. on player join, when the runtime contribution map is empty
 * again after being rebuilt from scratch). Reads what's equipped from
 * {@link PlayerAccessoryEquipmentComponent} (a virtual, GUI-driven slot set), not the
 * player's real inventory.
 */
public final class AccessoryEffectService {

    private final StatusService statusService;
    private final AccessoryIdentityService identityService;
    private final PlayerDataManager playerDataManager;

    public AccessoryEffectService(StatusService statusService, AccessoryIdentityService identityService,
                                   PlayerDataManager playerDataManager) {
        this.statusService = statusService;
        this.identityService = identityService;
        this.playerDataManager = playerDataManager;
    }

    private static String sourceKey(AccessoryType type) {
        return "accessory:" + type.name();
    }

    public void applyFromSlot(Player player, AccessoryType type) {
        ItemStack equipped = equippedItem(player, type);
        AccessoryData data = identityService.dataOf(equipped).orElse(null);
        if (data == null || data.getType() != type) {
            clear(player, type);
            return;
        }
        statusService.setEquipmentContribution(player.getUniqueId(), sourceKey(type), data.getStatBonus());
    }

    public void clear(Player player, AccessoryType type) {
        statusService.clearEquipmentContribution(player.getUniqueId(), sourceKey(type));
    }

    public void syncAll(Player player) {
        for (AccessoryType type : AccessoryType.values()) {
            applyFromSlot(player, type);
        }
    }

    private ItemStack equippedItem(Player player, AccessoryType type) {
        return playerDataManager.get(player.getUniqueId())
                .flatMap(data -> data.component(PlayerAccessoryEquipmentComponent.class))
                .map(component -> component.getSlot(type))
                .orElse(null);
    }
}
