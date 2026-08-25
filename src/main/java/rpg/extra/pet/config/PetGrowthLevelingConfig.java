package rpg.extra.pet.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code config.yml: pet.growth} - the tuning knob for how much growth XP a kill grants
 * (the per-level stat curve itself is per-species template data, see
 * {@code pets.yml}'s {@code growth:} section / {@link rpg.extra.pet.model.PetDefinition.PetGrowthTemplate}).
 */
public final class PetGrowthLevelingConfig {

    private long xpPerKill = 5;

    public void load(YamlConfiguration config) {
        this.xpPerKill = config.getLong("pet.growth.xp-per-kill", 5);
    }

    /** Growth XP granted to the player's currently-selected pet for each tagged monster killed while it's summoned. */
    public long getXpPerKill() {
        return xpPerKill;
    }
}
