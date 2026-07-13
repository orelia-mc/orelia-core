package rpg.gathering.model;

import java.util.UUID;

/**
 * A pending "restore this coordinate back to {@code originalMaterial}" task (SOW 3.1).
 * {@code originalMaterial} is stored as a {@link org.bukkit.Material} enum name so it
 * round-trips through the database the same way every other module persists an enum.
 */
public record BlockRegenTask(UUID id, String world, int x, int y, int z, String originalMaterial, long restoreAtMillis) {
}
