package rpg.world.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-plugin surface over the quest module, published by orelia-world the same way
 * orelia-core publishes {@code rpg.api} - so orelia-extra (Achievement, ...) can react to
 * quest completion without depending on {@code rpg.quest} internals.
 */
public interface QuestApi {

    /** Quest ids currently in progress (accepted but not yet turned in) for this player. */
    Set<String> getActiveQuestIds(UUID playerId);

    /** Whether the player has ever completed the given quest id. */
    boolean hasCompletedQuest(UUID playerId, String questId);

    /** The title currently equipped (see {@code /ol title equip}), shown by e.g. orelia-serverutil's {@code {title}} placeholder. Empty if none is equipped. */
    Optional<String> getEquippedTitle(UUID playerId);

    /** Every title this player has ever earned as a quest reward. */
    Set<String> getEarnedTitles(UUID playerId);

    /** Equips {@code title} as the player's displayed title. Returns false if the player hasn't earned it, or their data isn't loaded. */
    boolean equipTitle(UUID playerId, String title);
}
