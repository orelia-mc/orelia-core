package rpg.skill.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.service.SkillCastService;
import rpg.skill.service.SkillSocketService;

import java.util.List;
import java.util.Optional;

/**
 * Skill activation triggers for melee weapons (SWORD/SPEAR/AXE): right-click casts the
 * weapon's first socketed skill, shift+right-click the second, and the swap-hands key (F)
 * the third. Each trigger only cancels its underlying vanilla event (block interaction /
 * hand swap) when a skill actually occupied that socket, so an empty slot falls back to
 * normal behavior. BOW is excluded from the right-click triggers - right-click is vanilla's
 * draw-and-shoot action, and bow skill activation is being redesigned separately to fire
 * off the normal shot instead; the F-key trigger still works for bows in the meantime.
 */
public final class SkillActivationListener implements Listener {

    private final SkillCastService castService;
    private final SkillSocketService socketService;
    private final WeaponIdentityService weaponIdentityService;

    public SkillActivationListener(SkillCastService castService, SkillSocketService socketService,
                                    WeaponIdentityService weaponIdentityService) {
        this.castService = castService;
        this.socketService = socketService;
        this.weaponIdentityService = weaponIdentityService;
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
        if (weaponType == null || weaponType == WeaponType.BOW) {
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
        failure.ifPresent(f -> player.sendMessage(Component.text(describe(f), NamedTextColor.RED)));
        return true;
    }

    private String describe(SkillCastService.CastFailure failure) {
        return switch (failure) {
            case UNKNOWN_SKILL, NO_EXECUTOR -> "このスキルは設定されていません。";
            case WRONG_WEAPON -> "この武器ではこのスキルを使用できません。";
            case NOT_SOCKETED -> "武器にスキルが装着されていません。";
            case NOT_LEARNED -> "このスキルをまだ習得していません。";
            case ON_COOLDOWN -> "スキルはクールタイム中です。";
            case NOT_ENOUGH_SP -> "SPが足りません。";
        };
    }
}
