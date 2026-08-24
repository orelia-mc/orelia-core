package rpg.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Cross-plugin player display info (level/job/guild/party/title), aggregated across
 * OreliaCore/OreliaExtra/OreliaWorld's own published APIs. Interface only - the implementation
 * lives in orelia-serverutil (its {@code PlaceholderService} already aggregates all of these
 * for placeholder resolution), not here: aggregating {@code GuildApi} (orelia-extra) or
 * {@code QuestApi} (orelia-world) directly inside orelia-core would reverse the suite's
 * one-way dependency direction (orelia-world/orelia-extra/orelia-serverutil all depend on
 * orelia-core, never the other way). orelia-serverutil registers its aggregator under this
 * interface via {@code ServicesManager} so other plugins (chiefly orelia-extra's chat, for its
 * player-name hover card) can consume it without depending on orelia-serverutil directly - see
 * dynamic-chat-design.md.
 */
public interface PlayerProfileApi {

    /** Empty if {@code playerId} is offline or has no loaded data anywhere. */
    Optional<PlayerProfile> getProfile(UUID playerId);
}
