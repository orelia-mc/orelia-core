package rpg.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Cross-plugin surface over the job module.
 */
public interface JobApi {

    /** Current job identifier (e.g. {@code "FENCER"}), or empty if unemployed/not loaded. */
    Optional<String> getCurrentJob(UUID playerId);

    /** Current job's localized display name (e.g. {@code "フェンサー"}), or empty if unemployed/not loaded. */
    Optional<String> getCurrentJobDisplayName(UUID playerId);

    boolean canUseWeaponType(UUID playerId, String weaponType);

    /** Returns false if {@code jobName} has no matching jobs.yml definition. */
    boolean changeJob(UUID playerId, String jobName);
}
