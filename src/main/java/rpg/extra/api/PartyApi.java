package rpg.extra.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-plugin surface exposing a player's party membership (e.g. for orelia-serverutil's
 * placeholder system, and orelia-world's dungeon party resolution). Published via Bukkit's
 * {@code ServicesManager} by {@link ExtraApiModule}.
 */
public interface PartyApi {

    boolean isInParty(UUID playerId);

    /** The party leader's uuid, or empty if {@code playerId} isn't in a party. */
    Optional<UUID> getLeaderId(UUID playerId);

    /** Every member's uuid (including the leader), or an empty set if not in a party. */
    Set<UUID> getMemberIds(UUID playerId);
}
