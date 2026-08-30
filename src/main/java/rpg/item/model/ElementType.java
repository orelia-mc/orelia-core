package rpg.item.model;

/**
 * Elemental attribute a weapon (or monster resistance, see AccessoryModule 護符) can carry.
 */
public enum ElementType {
    NONE,
    FIRE,
    WATER,
    EARTH,
    WIND,
    LIGHT,
    DARK;

    /** {@code ColorUtil}-style custom color code used to tint this element's damage number - {@code null} for {@link #NONE} (caller falls back to its own default). */
    public String getColorCode() {
        return switch (this) {
            case NONE -> null;
            case FIRE -> "&%c";
            case WATER -> "&%b";
            case EARTH -> "&%2";
            case WIND -> "&%a";
            case LIGHT -> "&%e";
            case DARK -> "&%5";
        };
    }

    /** Japanese label for player-facing display (weapon lore, relic line description, ...) - never show a raw enum constant name to a player. */
    public String getDisplayLabel() {
        return switch (this) {
            case NONE -> "無";
            case FIRE -> "火";
            case WATER -> "水";
            case EARTH -> "土";
            case WIND -> "風";
            case LIGHT -> "光";
            case DARK -> "闇";
        };
    }
}
