package rpg.api;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-plugin surface over the item (weapon) module.
 */
public interface ItemApi {

    Optional<ItemStack> createWeapon(String weaponId);

    /** Resolves an ItemStack back to the weapon id it was generated from, if any. */
    Optional<String> identifyWeapon(ItemStack stack);

    Set<String> getAllWeaponIds();

    /** Whether {@code playerId} currently meets the job/level requirement to use this weapon id. */
    boolean weaponMeetsRequirements(UUID playerId, String weaponId);

    int getEnhancementLevel(ItemStack stack);

    /** Increments the weapon's enhancement level by one and returns the new level. */
    int enhanceWeapon(ItemStack stack);

    /**
     * Current weapon level (distinct from enhancement level) - starts at the weapon type's
     * {@code items.yml} {@code level:} and grows via {@link #levelUpWeapon}.
     */
    int getWeaponLevel(ItemStack stack);

    /**
     * Attempts to raise {@code stack}'s weapon level by one, gated by {@code playerId}'s
     * character level. Returns the new level, or {@code -1} if the player's level isn't high
     * enough to unlock the next weapon level yet.
     */
    int levelUpWeapon(UUID playerId, ItemStack stack);

    /**
     * Highest weapon level {@code playerId} is currently allowed to raise a weapon to
     * ({@link rpg.item.service.WeaponIdentityService#weaponLevelCap}) - lets a caller (e.g. an
     * NPC) check whether {@link #levelUpWeapon} would succeed before charging any cost for it.
     */
    int getWeaponLevelCap(UUID playerId);

    /**
     * Re-renders {@code stack}'s lore against its current weapon level/enhancement - call after
     * {@link #levelUpWeapon}/{@link #enhanceWeapon} so a caller outside this module (e.g. an
     * NPC interaction) sees the same immediate lore refresh {@code /ol item levelup} already
     * gets internally. No-op if {@code stack} isn't a weapon this module recognizes.
     */
    void refreshWeaponLore(ItemStack stack);
}
