package rpg.skill.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.service.SkillCastService;
import rpg.skill.service.SkillSocketService;

import java.util.List;
import java.util.Optional;

/**
 * Skill activation triggers for melee weapons (SWORD/SPEAR/AXE/PICKAXE/HATCHET): right-click
 * casts the weapon's first socketed skill, shift+right-click the second, and the swap-hands
 * key (F) the third. Each trigger only cancels its underlying vanilla event (block
 * interaction / hand swap) when a skill actually occupied that socket, so an empty slot
 * falls back to normal behavior. BOW and HOE are excluded from the right-click triggers -
 * right-click is vanilla's draw-and-shoot action for a bow (bow skill activation is being
 * redesigned separately to fire off the normal shot instead) and vanilla's till-farmland
 * action for a hoe; the F-key trigger still works for both in the meantime.
 */
public final class SkillActivationListener implements Listener {

    private final SkillCastService castService;
    private final SkillSocketService socketService;
    private final WeaponIdentityService weaponIdentityService;
    private final MessageManager messages;

    public SkillActivationListener(SkillCastService castService, SkillSocketService socketService,
                                    WeaponIdentityService weaponIdentityService, MessageManager messages) {
        this.castService = castService;
        this.socketService = socketService;
        this.weaponIdentityService = weaponIdentityService;
        this.messages = messages;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        WeaponType weaponType = weaponIdentityService.dataOf(mainHand).map(w -> w.getWeaponType()).orElse(null);
        if (weaponType == null || weaponType == WeaponType.BOW || weaponType == WeaponType.HOE) {
            return;
        }

        int slotIndex = player.isSneaking() ? 1 : 0;
        if (castSlot(player, slotIndex)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (castSlot(event.getPlayer(), 2)) {
            event.setCancelled(true);
        }
    }

    /** Returns true if a skill occupied that socket slot (regardless of whether the cast itself succeeded). */
    private boolean castSlot(Player player, int slotIndex) {
        List<String> socketed = socketService.getSocketedSkills(player.getInventory().getItemInMainHand());
        if (slotIndex >= socketed.size()) {
            return false;
        }
        Optional<SkillCastService.CastFailure> failure = castService.cast(player, socketed.get(slotIndex));
        failure.ifPresent(f -> messages.send(player, messageKey(f)));
        return true;
    }

    private String messageKey(SkillCastService.CastFailure failure) {
        return switch (failure) {
            case UNKNOWN_SKILL, NO_EXECUTOR -> "skill.unknown";
            case WRONG_WEAPON -> "skill.wrong-weapon";
            case NOT_SOCKETED -> "skill.not-socketed";
            case NOT_LEARNED -> "skill.not-learned";
            case ON_COOLDOWN -> "skill.on-cooldown";
            case NOT_ENOUGH_SP -> "skill.not-enough-sp";
        };
    }
}
