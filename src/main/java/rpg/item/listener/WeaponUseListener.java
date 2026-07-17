package rpg.item.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.item.model.WeaponData;
import rpg.item.service.WeaponIdentityService;
import rpg.item.service.WeaponRequirementService;
import rpg.util.MathUtil;

/**
 * Turns a melee hit with an Orelia weapon into base damage from {@link WeaponData}
 * (attack power, crit roll). Runs at {@link EventPriority#LOW} so the status module's
 * ATK/DEF combat listener (default priority) applies its percentage modifiers on top of
 * this base value instead of overwriting it.
 */
public final class WeaponUseListener implements Listener {

    /**
     * Metadata key the skill module sets on the caster while a skill's own damage event
     * is in flight, so this listener does not overwrite the skill's damage with the
     * weapon's plain attack power. See {@code rpg.skill.executor}.
     */
    public static final String SKILL_OVERRIDE_METADATA = "orelia_skill_active";

    private final WeaponIdentityService identityService;
    private final WeaponRequirementService requirementService;
    private final MessageManager messages;

    public WeaponUseListener(WeaponIdentityService identityService, WeaponRequirementService requirementService,
                              MessageManager messages) {
        this.identityService = identityService;
        this.requirementService = requirementService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWeaponHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (attacker.hasMetadata(SKILL_OVERRIDE_METADATA)) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        WeaponData data = identityService.dataOf(weapon).orElse(null);
        if (data == null) {
            return;
        }

        if (!requirementService.meetsRequirements(attacker.getUniqueId(), data)) {
            event.setCancelled(true);
            messages.send(attacker, "item.requirement-not-met");
            return;
        }

        double damage = data.getAttackPower() * identityService.enhancementMultiplier(weapon);
        if (MathUtil.rollChance(data.getCritRate())) {
            damage *= data.getCritMultiplier();
        }
        event.setDamage(damage);
    }
}
