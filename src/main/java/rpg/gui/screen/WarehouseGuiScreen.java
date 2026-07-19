package rpg.gui.screen;

import org.bukkit.entity.Player;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.repository.WarehouseRepository;

/**
 * Personal storage screen (SOW section 17 "倉庫"). Unlike the other screens this one
 * allows free item movement; {@link rpg.gui.listener.WarehouseSaveListener} persists the
 * contents back to {@link WarehouseRepository} when the player closes it.
 */
public final class WarehouseGuiScreen {

    public static final String TAG = "warehouse";

    private final WarehouseRepository repository;
    private final GuiConfig guiConfig;

    public WarehouseGuiScreen(WarehouseRepository repository, GuiConfig guiConfig) {
        this.repository = repository;
        this.guiConfig = guiConfig;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("warehouse", "&%8倉庫"), WarehouseRepository.SIZE).allowItemMovement().tag(TAG);
        var inventory = gui.toInventory();
        inventory.setContents(repository.load(player.getUniqueId()));
        return gui;
    }
}
