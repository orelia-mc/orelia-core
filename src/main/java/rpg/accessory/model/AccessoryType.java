package rpg.accessory.model;

/**
 * The four accessory categories (SOW section 8), each bound to one fixed slot in the
 * player's bottom inventory row.
 */
public enum AccessoryType {
    CHARM("お守り"),
    RING("指輪"),
    NECKLACE("ネックレス"),
    WING("羽根");

    private final String displayName;

    AccessoryType(String displayName) {
        this.displayName = displayName;
    }

    /** Japanese label for UI display (e.g. an empty accessory slot in {@code EquipmentGuiScreen}) - not used for config/PDC identity. */
    public String getDisplayName() {
        return displayName;
    }
}
