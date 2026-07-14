package rpg.accessory.manager;

import rpg.accessory.model.AccessoryType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fixed mapping between {@link AccessoryType} and a slot index in
 * {@link org.bukkit.inventory.PlayerInventory#getStorageContents()} (SOW section 8: the
 * bottom row of the main inventory, i.e. indices 27-35, the row directly above the
 * hotbar). Slots 31-35 are reserved for future accessory types.
 */
public final class AccessorySlotLayout {

    private static final Map<AccessoryType, Integer> TYPE_TO_SLOT = new EnumMap<>(AccessoryType.class);
    private static final Map<Integer, AccessoryType> SLOT_TO_TYPE = new java.util.HashMap<>();

    static {
        TYPE_TO_SLOT.put(AccessoryType.CHARM, 27);
        TYPE_TO_SLOT.put(AccessoryType.RING, 28);
        TYPE_TO_SLOT.put(AccessoryType.NECKLACE, 29);
        TYPE_TO_SLOT.put(AccessoryType.WING, 30);
        TYPE_TO_SLOT.forEach((type, slot) -> SLOT_TO_TYPE.put(slot, type));
    }

    private AccessorySlotLayout() {
    }

    public static int slotFor(AccessoryType type) {
        return TYPE_TO_SLOT.get(type);
    }

    public static Optional<AccessoryType> typeAtSlot(int slot) {
        return Optional.ofNullable(SLOT_TO_TYPE.get(slot));
    }

    public static boolean isAccessoryRow(int slot) {
        return slot >= 27 && slot <= 35;
    }
}
