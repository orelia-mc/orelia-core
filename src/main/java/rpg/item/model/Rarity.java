package rpg.item.model;

/**
 * Weapon rarity tiers. Order matters (ordinal is used for GUI sorting), colors are
 * {@code &}-codes used to prefix generated item names.
 */
public enum Rarity {
    COMMON("&f"),
    UNCOMMON("&a"),
    RARE("&b"),
    EPIC("&d"),
    LEGENDARY("&6");

    private final String colorCode;

    Rarity(String colorCode) {
        this.colorCode = colorCode;
    }

    public String getColor() {
        return colorCode;
    }
}
