package rpg.extra.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Cross-plugin surface exposing a player's guild name/tag (e.g. for orelia-serverutil's
 * placeholder system). Published via Bukkit's {@code ServicesManager} by {@link ExtraApiModule}.
 */
public interface GuildApi {

    Optional<String> getGuildName(UUID playerId);

    Optional<String> getGuildTag(UUID playerId);
}
