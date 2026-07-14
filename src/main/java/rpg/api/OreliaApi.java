package rpg.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public, stable read-only facade for third-party plugins (SOW section 19). Obtain it
 * through Bukkit's {@link org.bukkit.plugin.ServicesManager}:
 * <pre>{@code
 * RegisteredServiceProvider<OreliaApi> registration = Bukkit.getServicesManager().getRegistration(OreliaApi.class);
 * OreliaApi api = registration != null ? registration.getProvider() : null;
 * }</pre>
 * For anything beyond read-only lookups (granting rewards, opening GUIs, changing jobs, ...)
 * use the narrower {@link StatusApi}, {@link JobApi}, {@link ItemApi}, {@link AccessoryApi},
 * {@link SkillApi}, {@link GuiApi} and {@link EffectApi} interfaces published alongside this
 * one - that is also how orelia-world talks to orelia-core internally. Quest data lives in
 * orelia-world's own published API, not here.
 */
public interface OreliaApi {

    /** Character level, or empty if the player's data has not finished loading. */
    Optional<Integer> getPlayerLevel(UUID playerId);

    /** Current job name (e.g. {@code "FENCER"}), or empty if unemployed/not loaded. */
    Optional<String> getPlayerJob(UUID playerId);

    /** Final (post equipment/buff) stat values keyed by stat name (HP, SP, ATK, DEF, CRT, CRT_DMG, SPD). */
    Map<String, Double> getPlayerStats(UUID playerId);

    /** The weapon id of the item currently held in the player's main hand, if it is an Orelia weapon. */
    Optional<String> getHeldWeaponId(UUID playerId);

    /** Every weapon id defined in items.yml. */
    Set<String> getAllWeaponIds();
}
