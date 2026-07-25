package rpg.api;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Cross-plugin surface for generating relics (boss-dropped rollable accessories, see
 * {@code rpg.relic}) - the minimal API orelia-world needs to grant a relic on a dungeon boss
 * kill, mirroring {@link CombatApi#spawnMonster}/{@link CombatApi#spawnBoss}.
 */
public interface RelicApi {

    /** Empty if {@code relics.yml} has no parts/pools configured to roll from. */
    Optional<ItemStack> generateRelic(String sourceDungeonId);
}
