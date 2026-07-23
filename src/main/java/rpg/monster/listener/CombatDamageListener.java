package rpg.monster.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import rpg.core.message.MessageManager;
import rpg.item.model.ElementType;
import rpg.item.model.WeaponData;
import rpg.item.service.WeaponIdentityService;
import rpg.item.service.WeaponRequirementService;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterSpawnService;
import rpg.status.combat.DamageFormula;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.ScaledHealthService;
import rpg.status.service.StatusService;

/**
 * Single source of truth for every melee/monster damage event: works out the attacker's base
 * attack power (weapon, bare hand, monster, or skill) and the victim's defense, then runs them
 * through {@link DamageFormula#compute} in a fixed order (ATK% -&gt; DEF -&gt; crit -&gt;
 * elemental weakness). Replaces the old {@code WeaponUseListener}/{@code CombatStatusListener}/
 * {@code MonsterCombatListener} trio, whose damage-setting logic was split across listeners at
 * the same {@link EventPriority#LOW} priority and relied on Bukkit's undefined same-priority
 * ordering to land crit before ATK%/DEF instead of after.
 *
 * <p>When {@code SkillDamage} sets {@link DamageFormula#SKILL_OVERRIDE_METADATA} on the caster,
 * {@code event.getDamage()} already holds the skill's base attack power with ATK% folded in
 * (computed once per cast, not per target - AOE/cone skills hit several entities with the same
 * base amount). This listener still resolves DEF/crit/elemental weakness against the specific
 * victim of *this* event, since those steps are inherently per-target and can't be
 * precomputed for a multi-target skill.
 */
public final class CombatDamageListener implements Listener {

    private final Plugin plugin;
    private final WeaponIdentityService identityService;
    private final WeaponRequirementService requirementService;
    private final StatusService statusService;
    private final MonsterSpawnService spawnService;
    private final MessageManager messages;

    public CombatDamageListener(Plugin plugin, WeaponIdentityService identityService, WeaponRequirementService requirementService,
                                 StatusService statusService, MonsterSpawnService spawnService, MessageManager messages) {
        this.plugin = plugin;
        this.identityService = identityService;
        this.requirementService = requirementService;
        this.statusService = statusService;
        this.spawnService = spawnService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageByEntityEvent event) {
        AttackInput attack = resolveAttack(event);
        if (attack == null) {
            return; // event was cancelled (weapon requirement not met)
        }

        double defense = resolveDefense(event.getEntity());
        boolean weak = isWeaknessHit(event);

        DamageFormula.DamageResult result = DamageFormula.compute(
                attack.baseAttackPower(), attack.atkPercent(), defense,
                attack.critRate(), attack.critMultiplier(), attack.critDmgPercent(),
                weak, DamageFormula.DEFAULT_WEAKNESS_MULTIPLIER);

        event.setDamage(resolveFinalDamage(event.getEntity(), result.amount()));
        applyCritMetadata(event.getDamager(), result.crit());
    }

    /**
     * {@code result.amount()} is in "scaled" units (a player's/tagged monster's HP pool can be
     * in the hundreds or thousands while their real vanilla health stays in a small, engine-safe
     * range) - for a scaled victim, converts it to the vanilla-equivalent amount for
     * {@code event.setDamage} (so Bukkit's own event resolution still applies knockback/hurt
     * sound/death normally) and separately reduces the tracked scaled current HP by the
     * original amount. Anything else (an untagged vanilla mob, or environmental damage which
     * never reaches this listener at all) passes the amount through unchanged.
     */
    private double resolveFinalDamage(Entity victim, double scaledDamage) {
        if (victim instanceof Player player) {
            double scaledMax = statusService.getFinalStats(player.getUniqueId()).map(stats -> stats.get(StatType.HP)).orElse(0.0);
            double vanillaDamage = ScaledHealthService.convertDamageToVanilla(player, scaledDamage, scaledMax);
            statusService.applyScaledCombatDamage(player.getUniqueId(), scaledDamage);
            // DamageDisplayListener reads this instead of event.getFinalDamage() so the
            // floating number shows the meaningful scaled amount, not the tiny vanilla one.
            player.setMetadata(DamageFormula.SCALED_DAMAGE_METADATA_KEY, new FixedMetadataValue(plugin, scaledDamage));
            return vanillaDamage;
        }
        if (victim instanceof LivingEntity living) {
            MonsterData data = spawnService.dataOf(living).orElse(null);
            if (data != null) {
                double vanillaDamage = ScaledHealthService.convertDamageToVanilla(living, scaledDamage, spawnService.scaledMaxHpOf(living, data));
                spawnService.applyScaledCombatDamage(living, data, scaledDamage);
                living.setMetadata(DamageFormula.SCALED_DAMAGE_METADATA_KEY, new FixedMetadataValue(plugin, scaledDamage));
                return vanillaDamage;
            }
        }
        return scaledDamage;
    }

    /** Returns {@code null} if the event was cancelled and processing should stop here. */
    private AttackInput resolveAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            WeaponData data = identityService.dataOf(weapon).orElse(null);
            StatSheet stats = statusService.getFinalStats(attacker.getUniqueId()).orElse(null);
            double critDmg = stats != null ? stats.get(StatType.CRT_DMG) : 0;
            double weaponCritRate = (data != null ? data.getCritRate() : 0.0) + (stats != null ? stats.get(StatType.CRT) : 0);
            double critMultiplier = data != null ? data.getCritMultiplier() : DamageFormula.DEFAULT_CRIT_MULTIPLIER;

            if (attacker.hasMetadata(DamageFormula.SKILL_OVERRIDE_METADATA)) {
                // SkillDamage already folded base attack power + ATK% into event.getDamage()
                // (once per cast, not per target) - only DEF/crit/weakness are left to resolve
                // against this specific victim.
                return new AttackInput(event.getDamage(), 0, weaponCritRate, critMultiplier, critDmg);
            }

            double atkPercent = stats != null ? stats.get(StatType.ATK) : 0;
            if (data != null) {
                if (!requirementService.meetsRequirements(attacker.getUniqueId(), data)) {
                    event.setCancelled(true);
                    messages.send(attacker, "item.requirement-not-met");
                    return null;
                }
                double baseAttackPower = identityService.baseAttackPower(weapon, data);
                return new AttackInput(baseAttackPower, atkPercent, weaponCritRate, critMultiplier, critDmg);
            }

            // Bare hand: the player's own ATK stat IS the base attack power directly - no
            // separate ATK% layer on top of itself (that would double-count the same stat).
            double critRate = stats != null ? stats.get(StatType.CRT) : 0;
            return new AttackInput(atkPercent, 0, critRate, DamageFormula.DEFAULT_CRIT_MULTIPLIER, critDmg);
        }

        if (event.getDamager() instanceof LivingEntity attacker) {
            MonsterData data = spawnService.dataOf(attacker).orElse(null);
            if (data != null) {
                return new AttackInput(spawnService.scaledAttackPowerOf(attacker, data), 0, data.getCritRate(), data.getCritMultiplier(), 0);
            }
        }

        // Unrecognized attacker (vanilla mob, projectile, environmental damage, ...) - keep
        // vanilla damage as the base and skip ATK%/crit, but still let DEF mitigate it below.
        return new AttackInput(event.getDamage(), 0, 0, DamageFormula.DEFAULT_CRIT_MULTIPLIER, 0);
    }

    private double resolveDefense(Entity victim) {
        if (victim instanceof Player player) {
            return statusService.getFinalStats(player.getUniqueId()).map(stats -> stats.get(StatType.DEF)).orElse(0.0);
        }
        if (victim instanceof LivingEntity living) {
            return spawnService.dataOf(living).map(data -> spawnService.scaledDefenseOf(living, data)).orElse(0.0);
        }
        return 0.0;
    }

    private boolean isWeaknessHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !(event.getDamager() instanceof Player attacker)) {
            return false;
        }
        MonsterData data = spawnService.dataOf(victim).orElse(null);
        if (data == null || data.getWeakness() == ElementType.NONE) {
            return false;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        return identityService.dataOf(weapon)
                .map(WeaponData::getElement)
                .map(element -> element == data.getWeakness())
                .orElse(false);
    }

    private void applyCritMetadata(Entity damager, boolean crit) {
        if (!(damager instanceof LivingEntity attacker)) {
            return;
        }
        if (crit) {
            attacker.setMetadata(DamageFormula.CRIT_METADATA_KEY, new FixedMetadataValue(plugin, true));
        } else {
            attacker.removeMetadata(DamageFormula.CRIT_METADATA_KEY, plugin);
        }
    }

    private record AttackInput(double baseAttackPower, double atkPercent, double critRate, double critMultiplier, double critDmgPercent) {
    }
}
