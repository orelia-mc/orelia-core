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
import rpg.gui.service.ActionBarService;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.model.SkillData;
import rpg.skill.repository.SkillRepository;
import rpg.skill.service.SkillCastService;
import rpg.skill.service.SkillSocketService;

import java.util.List;
import java.util.Optional;

/**
 * Skill activation triggers for melee weapons (SWORD/SPEAR/AXE/PICKAXE/HATCHET): right-click
 * casts the weapon's first socketed skill, and the swap-hands key (F, cancelling the vanilla
 * item swap) casts the second - no weapon's {@code items.yml} {@code skill-slot-count} exceeds
 * 2 today, so these two triggers cover every socket that can actually be filled. (Previously
 * the second socket cast on shift+right-click instead; that was dropped in favor of F since
 * shift+right-click also means "sneak while attacking," which reads as an odd key for casting
 * a skill compared to the swap-hands key most players already associate with a secondary
 * action.) Each trigger only cancels its underlying vanilla event (block interaction / hand
 * swap) when a skill actually occupied that socket, so an empty slot falls back to normal
 * behavior. BOW and HOE are excluded from the right-click trigger - right-click is vanilla's
 * draw-and-shoot action for a bow (bow skill activation is being redesigned separately to fire
 * off the normal shot instead) and vanilla's till-farmland action for a hoe; the F-key trigger
 * still works for both in the meantime.
 *
 * <p>Cast feedback (success/on-cooldown/etc.) goes through {@link ActionBarService#showTransient}
 * rather than chat - a player casting repeatedly (e.g. spamming right-click while a skill is on
 * cooldown) would otherwise flood their own chat with the same message every attempt.
 */
public final class SkillActivationListener implements Listener {

    private static final long FEEDBACK_DURATION_MILLIS = 2000L;

    private final SkillCastService castService;
    private final SkillSocketService socketService;
    private final WeaponIdentityService weaponIdentityService;
    private final SkillRepository skillRepository;
    private final ActionBarService actionBarService;
    private final MessageManager messages;

    public SkillActivationListener(SkillCastService castService, SkillSocketService socketService,
                                    WeaponIdentityService weaponIdentityService, SkillRepository skillRepository,
                                    ActionBarService actionBarService, MessageManager messages) {
        this.castService = castService;
        this.socketService = socketService;
        this.weaponIdentityService = weaponIdentityService;
        this.skillRepository = skillRepository;
        this.actionBarService = actionBarService;
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

        if (castSlot(player, 0)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (castSlot(event.getPlayer(), 1)) {
            event.setCancelled(true);
        }
    }

    /** Returns true if a skill occupied that socket slot (regardless of whether the cast itself succeeded). */
    private boolean castSlot(Player player, int slotIndex) {
        List<String> socketed = socketService.getSocketedSkills(player.getInventory().getItemInMainHand());
        if (slotIndex >= socketed.size()) {
            return false;
        }
        String skillId = socketed.get(slotIndex);
        Optional<SkillCastService.CastFailure> failure = castService.cast(player, skillId);
        String feedback = failure.isPresent()
                ? messages.format(messageKey(failure.get()))
                : messages.format("skill.cast-success", "skill", skillName(skillId));
        actionBarService.showTransient(player, feedback, FEEDBACK_DURATION_MILLIS);
        return true;
    }

    private String skillName(String skillId) {
        return skillRepository.findById(skillId).map(SkillData::getName).orElse(skillId);
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
