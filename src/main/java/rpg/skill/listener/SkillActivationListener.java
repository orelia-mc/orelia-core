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
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.model.SkillData;
import rpg.skill.repository.SkillRepository;
import rpg.skill.service.SkillCastService;
import rpg.skill.service.SkillSocketService;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Skill activation triggers, split by whether a weapon type's own vanilla right-click action
 * can share the button with skill casting:
 *
 * <ul>
 *   <li>SWORD/AXE/PICKAXE/HATCHET (no right-click action of their own worth preserving):
 *       right-click casts socket 1, the swap-hands key (F, cancelling the vanilla item swap)
 *       casts socket 2.
 *   <li>{@link #RIGHT_CLICK_RESERVED} - BOW (draw-and-shoot), SPEAR (trident throw/Riptide), and
 *       HOE (till farmland) - right-click is never intercepted at all here, so that action
 *       always works exactly like vanilla whether or not a skill is socketed. Both of their
 *       sockets instead live on the swap-hands key: plain F casts socket 1, sneaking + F casts
 *       socket 2. (A trident with a skill socketed used to have its throw hijacked on every
 *       right-click - {@code castSlot} cancels the vanilla event whenever a skill occupied that
 *       slot, regardless of whether the cast itself succeeded - and a bow/crossbow/hoe's first
 *       socket was simply unreachable, since their right-click was already skipped entirely
 *       while F only ever targeted socket 2.)
 * </ul>
 *
 * No weapon's {@code items.yml} {@code skill-slot-count} exceeds 2 today, so between the two
 * physical buttons available per category, every socket that can actually be filled has a
 * working trigger. Each trigger only cancels its underlying vanilla event (block interaction /
 * hand swap) when a skill actually occupied that socket, so an empty slot falls back to normal
 * behavior.
 *
 * <p>Cast feedback (success/on-cooldown/etc.) goes through {@link ActionBarService#showTransient}
 * rather than chat - a player casting repeatedly (e.g. spamming right-click while a skill is on
 * cooldown) would otherwise flood their own chat with the same message every attempt.
 */
public final class SkillActivationListener implements Listener {

    private static final long FEEDBACK_DURATION_MILLIS = 2000L;

    /** Weapon types whose own right-click action must never be intercepted for skill casting - see the class javadoc. */
    public static final Set<WeaponType> RIGHT_CLICK_RESERVED = Set.of(WeaponType.BOW, WeaponType.SPEAR, WeaponType.HOE);

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
        WeaponType weaponType = weaponTypeOf(player);
        if (weaponType == null || RIGHT_CLICK_RESERVED.contains(weaponType)) {
            return;
        }

        if (castSlot(player, 0)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        WeaponType weaponType = weaponTypeOf(player);
        // For a right-click-reserved weapon, F alone reaches socket 1 (right-click can't, since
        // it must stay free for the weapon's own action) and sneak+F reaches socket 2. Every
        // other weapon already got socket 1 from right-click, so plain F always means socket 2.
        int slotIndex = weaponType != null && RIGHT_CLICK_RESERVED.contains(weaponType)
                ? (player.isSneaking() ? 1 : 0)
                : 1;
        if (castSlot(player, slotIndex)) {
            event.setCancelled(true);
        }
    }

    private WeaponType weaponTypeOf(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        return weaponIdentityService.dataOf(mainHand).map(WeaponData::getWeaponType).orElse(null);
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
