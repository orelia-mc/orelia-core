package rpg.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Cross-plugin surface for relics (boss-dropped rollable accessories, see {@code rpg.relic}) -
 * the minimal API orelia-world needs to grant one on a dungeon boss kill (mirroring
 * {@link CombatApi#spawnMonster}/{@link CombatApi#spawnBoss}) and to open the "選べる厳選"
 * upgrade GUI from an NPC (mirroring {@link GuiApi#openWarehouse}/{@link GuiApi#openJobChange}).
 */
public interface RelicApi {

    /** Empty if {@code relics.yml} has no parts/pools configured to roll from. */
    Optional<ItemStack> generateRelic(String sourceDungeonId);

    /**
     * Opens the substat upgrade GUI ({@code /ol relic upgrade}'s screen) for the relic
     * currently in {@code player}'s main hand, or sends them the same "not holding a relic"
     * message that command sends if they aren't holding one.
     */
    void openUpgradeGui(Player player);
}
