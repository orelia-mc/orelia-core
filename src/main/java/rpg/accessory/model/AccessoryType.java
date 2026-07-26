package rpg.accessory.model;

/**
 * The accessory categories (SOW section 8), each bound to one fixed slot of the virtual equip
 * slot set ({@link PlayerAccessoryEquipmentComponent}, indexed by {@link #ordinal()}).
 */
public enum AccessoryType {
    CHARM("お守り"),
    RING("指輪"),
    NECKLACE("ネックレス"),
    WING("羽根"),
    EARRING("耳飾り"),
    BELT("ベルト");

    private final String displayName;

    AccessoryType(String displayName) {
        this.displayName = displayName;
    }

    /** Japanese label for UI display (e.g. an empty equip slot in {@code StatusGuiScreen}) - not used for config/PDC identity. */
    public String getDisplayName() {
        return displayName;
    }
}
