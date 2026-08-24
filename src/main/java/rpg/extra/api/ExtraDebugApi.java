package rpg.extra.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-plugin surface for debug/testplay tooling (orelia-debug) over orelia-extra: config
 * inspection/editing (same pattern as orelia-core's {@code rpg.api.DebugApi} and orelia-world's
 * {@code rpg.world.api.WorldDebugApi}) plus force-opening the few extra GUIs that exist
 * (Auction/Mail/Ranking) for an arbitrary player.
 */
public interface ExtraDebugApi {

    Set<String> listConfigFiles();

    Optional<String> getConfigValue(String fileName, String path);

    boolean setConfigValue(String fileName, String path, String rawValue);

    void saveConfig(String fileName);

    List<String> describeConfigKeys(String fileName);

    /**
     * One node per key in {@code fileName}, in on-disk order, depth-first - for a human-readable
     * indented "config view" listing rather than a flat dot-path dump. Same shape as
     * orelia-core's {@code rpg.api.DebugApi.ConfigTreeEntry} / orelia-world's
     * {@code rpg.world.api.WorldDebugApi.ConfigTreeEntry}, duplicated here for the same reason.
     */
    List<ConfigTreeEntry> listConfigTree(String fileName);

    /** See {@link #listConfigTree}. {@code value} is {@code null} for a non-leaf (section) node. */
    record ConfigTreeEntry(String path, int depth, String label, String value, boolean isLeaf) {}

    void openAuction(Player player);

    void openMail(Player player);

    void openRanking(Player player);

    void openPet(Player player);

    void openHouse(Player player);

    void openAchievement(Player player);

    /** Force-opens the guild list screen (same as the new {@code GuildGuiScreen}) for {@code player}. */
    void openGuild(Player player);

    Set<String> listPetIds();

    Set<String> getUnlockedPets(UUID playerId);

    /** {@code false} if {@code petId} doesn't exist or is already unlocked for {@code playerId}. */
    boolean forceUnlockPet(UUID playerId, String petId);

    Set<String> listMountIds();

    Set<String> getUnlockedMounts(UUID playerId);

    /** {@code false} if {@code mountId} doesn't exist or is already unlocked for {@code playerId}. */
    boolean forceUnlockMount(UUID playerId, String mountId);

    Set<String> listHousePlotIds();

    Optional<String> getOwnedPlotId(UUID playerId);

    /** {@code false} if {@code plotId} doesn't exist, is taken, or {@code player} already owns a plot. */
    boolean forceGrantPlot(Player player, String plotId);

    /** {@code false} if {@code playerId} didn't own a plot. */
    boolean releasePlot(UUID playerId);

    Optional<UUID> getTradeCounterpart(UUID playerId);

    /** {@code false} if {@code playerId} wasn't in an active trade. */
    boolean forceCancelTrade(UUID playerId);
}
