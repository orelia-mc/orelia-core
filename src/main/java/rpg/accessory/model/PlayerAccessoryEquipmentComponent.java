package rpg.accessory.model;

import org.bukkit.inventory.ItemStack;
import rpg.core.player.PlayerDataComponent;

import java.util.UUID;

/**
 * Which accessory/relic (if any) the player has equipped in each {@link AccessoryType} slot -
 * a virtual, GUI-driven equivalent of a 6-slot inventory, persisted independently of the
 * player's real Bukkit inventory (see {@code rpg.accessory.repository.AccessoryEquipmentRepository}).
 * Indexed by {@link AccessoryType#ordinal()}.
 */
public final class PlayerAccessoryEquipmentComponent implements PlayerDataComponent {

    private final UUID owner;
    private final ItemStack[] slots;

    public PlayerAccessoryEquipmentComponent(UUID owner, ItemStack[] slots) {
        this.owner = owner;
        this.slots = slots.length == AccessoryType.values().length
                ? slots
                : new ItemStack[AccessoryType.values().length];
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public ItemStack getSlot(AccessoryType type) {
        return slots[type.ordinal()];
    }

    public void setSlot(AccessoryType type, ItemStack stack) {
        slots[type.ordinal()] = stack;
    }

    /** Raw backing array (live reference, not a copy) - used by the repository for serialization. */
    public ItemStack[] getSlots() {
        return slots;
    }
}
