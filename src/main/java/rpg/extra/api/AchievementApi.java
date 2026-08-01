package rpg.extra.api;

import org.bukkit.entity.Player;

/**
 * Opens the achievement GUI directly - lets a soft-dependent consumer (orelia-world's
 * player-info nether-star menu) launch the same screen {@code /ol achievement gui} does without
 * going through the command dispatcher. Published via Bukkit's {@code ServicesManager} by
 * {@link ExtraApiModule}.
 */
public interface AchievementApi {

    void openGui(Player player);
}
