package rpg.api;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Cross-plugin surface over the gui module. orelia-world's NPC module calls this to open
 * core-owned screens (shop/job-change/warehouse/...) instead of depending on
 * {@code rpg.gui} internals - GUI rendering code stays entirely inside orelia-core
 * (SOW coding rule: "GUI処理はGUIパッケージへ実装する").
 */
public interface GuiApi {

    void openStatus(Player player);

    /**
     * @deprecated the standalone equipment screen is gone - equipping now happens directly in
     *         the status screen's own slots, so this just opens {@link #openStatus}. Kept so
     *         already-published orelia-world/orelia-debug builds keep linking.
     */
    @Deprecated
    void openEquipment(Player player);

    void openSkill(Player player);

    void openJobChange(Player player);

    void openWarehouse(Player player);

    void openShop(Player player, List<ShopEntry> stock);

    void openCrafting(Player player);
}
