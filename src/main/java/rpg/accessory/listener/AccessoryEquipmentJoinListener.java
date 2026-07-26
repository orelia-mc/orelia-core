package rpg.accessory.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import rpg.accessory.service.AccessoryEffectService;
import rpg.relic.service.RelicEffectService;

/**
 * Applies every equipped accessory/relic's stat contribution on join, since those
 * contributions live in the player's status buffs (rebuilt fresh at load time), not in
 * {@code PlayerAccessoryEquipmentComponent} itself - see {@code AccessoryEffectService}/
 * {@code RelicEffectService}. Actual equip/unequip now happens entirely inside the status
 * GUI's interactive slots ({@code StatusEquipmentSlotListener}), which triggers the same
 * apply/clear calls directly on every change - this listener only needs to run once, at join.
 */
public final class AccessoryEquipmentJoinListener implements Listener {

    private final AccessoryEffectService effectService;
    private final RelicEffectService relicEffectService;

    public AccessoryEquipmentJoinListener(AccessoryEffectService effectService, RelicEffectService relicEffectService) {
        this.effectService = effectService;
        this.relicEffectService = relicEffectService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        effectService.syncAll(event.getPlayer());
        relicEffectService.syncAll(event.getPlayer());
    }
}
