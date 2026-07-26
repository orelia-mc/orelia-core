package rpg.gui.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import rpg.accessory.model.AccessoryType;
import rpg.accessory.model.PlayerAccessoryEquipmentComponent;
import rpg.accessory.repository.AccessoryEquipmentRepository;
import rpg.accessory.service.AccessoryEffectService;
import rpg.accessory.service.AccessoryIdentityService;
import rpg.core.player.PlayerDataManager;
import rpg.core.scheduler.SchedulerService;
import rpg.gui.framework.GuiHolder;
import rpg.gui.screen.StatusGuiScreen;
import rpg.relic.service.RelicEffectService;
import rpg.relic.service.RelicIdentityService;

/**
 * Makes the 6 equip slots of {@link StatusGuiScreen} actually equip things: enforces "only the
 * matching accessory type may go in its designated slot" (SOW section 8, previously enforced on
 * the player's own inventory row by {@code AccessorySlotListener}) and keeps the status module's
 * contribution in sync with {@link PlayerAccessoryEquipmentComponent}. Both the static
 * {@code rpg.accessory} items and the boss-dropped {@code rpg.relic} ones share these slots - a
 * slot holds exactly one or the other, so both effect services are told about every change and
 * each independently no-ops when the slot doesn't hold its kind of item.
 *
 * <p>The vanilla cursor swap is always cancelled and redone by hand rather than letting Bukkit
 * resolve the click natively (which is what {@code Gui#interactiveSlot} would allow). An empty
 * equip slot isn't really empty - it shows a labelled placeholder pane naming the part - and a
 * native swap would cheerfully hand that placeholder to the player as a free glass pane, once
 * per empty slot per screen open. Doing the exchange by hand also covers number-key/offhand
 * swaps and needs no "read the slot back one tick later" step: the component is the source of
 * truth and is updated before the click even returns. (Drags never reach here at all -
 * {@code GuiListener} cancels any drag touching a non-{@code interactiveSlot} top slot.)
 */
public final class StatusEquipmentSlotListener implements Listener {

    private final AccessoryIdentityService accessoryIdentityService;
    private final RelicIdentityService relicIdentityService;
    private final AccessoryEffectService effectService;
    private final RelicEffectService relicEffectService;
    private final AccessoryEquipmentRepository equipmentRepository;
    private final PlayerDataManager playerDataManager;
    private final SchedulerService schedulerService;

    public StatusEquipmentSlotListener(AccessoryIdentityService accessoryIdentityService,
                                        RelicIdentityService relicIdentityService,
                                        AccessoryEffectService effectService,
                                        RelicEffectService relicEffectService,
                                        AccessoryEquipmentRepository equipmentRepository,
                                        PlayerDataManager playerDataManager,
                                        SchedulerService schedulerService) {
        this.accessoryIdentityService = accessoryIdentityService;
        this.relicIdentityService = relicIdentityService;
        this.effectService = effectService;
        this.relicEffectService = relicEffectService;
        this.equipmentRepository = equipmentRepository;
        this.playerDataManager = playerDataManager;
        this.schedulerService = schedulerService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)
                || !StatusGuiScreen.TAG.equals(holder.getGui().getTag())
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!top.equals(event.getClickedInventory())) {
            return;
        }
        AccessoryType type = StatusGuiScreen.typeAtEquipSlot(event.getSlot()).orElse(null);
        if (type == null) {
            return;
        }
        event.setCancelled(true);

        PlayerAccessoryEquipmentComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(data -> data.component(PlayerAccessoryEquipmentComponent.class))
                .orElse(null);
        if (component == null) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack equipped = component.getSlot(type);
        ItemStack newlyEquipped;
        if (isEmpty(cursor)) {
            if (isEmpty(equipped)) {
                return; // clicking an empty slot with an empty hand - nothing to swap
            }
            newlyEquipped = null;
        } else {
            if (!matches(cursor, type)) {
                return; // wrong part for this slot; leave both the slot and the cursor alone
            }
            newlyEquipped = cursor.clone();
        }
        component.setSlot(type, newlyEquipped);
        // Persisted right away rather than only on the quit-time autosave, so a server crash
        // can't roll an equip back (same reasoning as WarehouseSaveListener saving on close).
        equipmentRepository.save(component);
        effectService.applyFromSlot(player, type);
        relicEffectService.applyFromSlot(player, type);

        ItemStack toCursor = isEmpty(equipped) ? null : equipped;
        int slot = event.getSlot();
        // A cancelled click makes the client roll its view back, so the new slot icon/cursor are
        // written a tick later - otherwise the rollback lands on top of them.
        schedulerService.runLater(() -> {
            if (top.equals(player.getOpenInventory().getTopInventory())) {
                top.setItem(slot, StatusGuiScreen.equipSlotIcon(type, newlyEquipped));
            }
            player.setItemOnCursor(toCursor);
            player.updateInventory();
        }, 1L);
    }

    private boolean matches(ItemStack stack, AccessoryType type) {
        boolean matchesStaticAccessory = accessoryIdentityService.dataOf(stack)
                .map(data -> data.getType() == type).orElse(false);
        boolean matchesRelic = relicIdentityService.read(stack)
                .map(instance -> instance.part() == type).orElse(false);
        return matchesStaticAccessory || matchesRelic;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }
}
