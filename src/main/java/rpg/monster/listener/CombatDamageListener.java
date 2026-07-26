package rpg.monster.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import rpg.core.message.MessageManager;
import rpg.item.model.ElementType;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
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
    private final ProjectileKeys projectileKeys;

    public CombatDamageListener(Plugin plugin, WeaponIdentityService identityService, WeaponRequirementService requirementService,
                                 StatusService statusService, MonsterSpawnService spawnService, MessageManager messages,
                                 ProjectileKeys projectileKeys) {
        this.plugin = plugin;
        this.identityService = identityService;
        this.requirementService = requirementService;
        this.statusService = statusService;
        this.spawnService = spawnService;
        this.messages = messages;
        this.projectileKeys = projectileKeys;
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
                weak, DamageFormula.DEFAULT_WEAKNESS_MULTIPLIER, attack.elementalDamageBonusPercent());

        event.setDamage(resolveFinalDamage(event.getEntity(), result.amount()));
        applyCritMetadata(event.getDamager(), result.crit());
        applyElementMetadata(event.getEntity(), attack.element());
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
        if (event.getDamager() instanceof EvokerFangs fangs && fangs.hasMetadata(DamageFormula.WAND_FANGS_METADATA_KEY)) {
            // Visual-only fangs summoned by the mage's wand ability - see WAND_FANGS_METADATA_KEY.
            // Damage was already applied manually by MagicWandAbilityListener; skip entirely
            // rather than cancelling, since this listener has no ignoreCancelled guard and would
            // otherwise still run its own (unwanted) damage side effects below.
            event.setCancelled(true);
            return null;
        }
        if (event.getDamager() instanceof Player attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            WeaponData rawData = identityService.dataOf(weapon).orElse(null);
            // A bow/crossbow's items.yml attack-power is tuned for the arrow it fires, not a
            // melee swing - without this, punching with one held (no projectile involved at
            // all) applied that same ranged attack-power directly, making melee hit harder
            // than actually shooting. Treated exactly like bare hand instead, same as holding
            // no Orelia weapon at all - the weapon-requirement gate below is skipped too,
            // since a bow held for a melee swing isn't really "using" it as a bow.
            WeaponData data = rawData != null && rawData.getWeaponType() != WeaponType.BOW ? rawData : null;
            StatSheet stats = statusService.getFinalStats(attacker.getUniqueId()).orElse(null);
            double critDmg = stats != null ? stats.get(StatType.CRT_DMG) : 0;
            double weaponCritRate = (data != null ? data.getCritRate() : 0.0) + (stats != null ? stats.get(StatType.CRT) : 0);
            double critMultiplier = data != null ? data.getCritMultiplier() : DamageFormula.DEFAULT_CRIT_MULTIPLIER;

            double elementalDamageBonus = elementalDamageBonusPercentFor(data != null ? data.getElement() : ElementType.NONE, stats);

            ElementType weaponElement = data != null ? data.getElement() : ElementType.NONE;

            if (attacker.hasMetadata(DamageFormula.SKILL_OVERRIDE_METADATA)) {
                // SkillDamage already folded base attack power + ATK% into event.getDamage()
                // (once per cast, not per target) - only DEF/crit/weakness are left to resolve
                // against this specific victim.
                return new AttackInput(event.getDamage(), 0, weaponCritRate, critMultiplier, critDmg, elementalDamageBonus, weaponElement);
            }

            double atkPercent = stats != null ? stats.get(StatType.ATK) : 0;
            if (data != null) {
                if (!requirementService.meetsRequirements(attacker.getUniqueId(), data)) {
                    event.setCancelled(true);
                    messages.send(attacker, "item.requirement-not-met");
                    return null;
                }
                double baseAttackPower = identityService.baseAttackPower(weapon, data);
                return new AttackInput(baseAttackPower, atkPercent, weaponCritRate, critMultiplier, critDmg, elementalDamageBonus, weaponElement);
            }

            // Bare hand (or a bow/crossbow being swung in melee, treated the same way): the
            // player's own ATK stat IS the base attack power directly - no separate ATK% layer
            // on top of itself (that would double-count the same stat).
            double critRate = stats != null ? stats.get(StatType.CRT) : 0;
            return new AttackInput(atkPercent, 0, critRate, DamageFormula.DEFAULT_CRIT_MULTIPLIER, critDmg, 0, ElementType.NONE);
        }

        if (event.getDamager() instanceof LivingEntity attacker) {
            MonsterData data = spawnService.dataOf(attacker).orElse(null);
            if (data != null) {
                return new AttackInput(spawnService.scaledAttackPowerOf(attacker, data), 0, data.getCritRate(), data.getCritMultiplier(), 0, 0, data.getElement());
            }
        }

        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            AttackInput projectileAttack = resolveProjectileAttack(projectile, shooter);
            if (projectileAttack != null) {
                return projectileAttack;
            }
        }

        // Unrecognized attacker (vanilla mob, unstamped projectile, environmental damage, ...) -
        // keep vanilla damage as the base and skip ATK%/crit, but still let DEF mitigate it below.
        return new AttackInput(event.getDamage(), 0, 0, DamageFormula.DEFAULT_CRIT_MULTIPLIER, 0, 0, ElementType.NONE);
    }

    /**
     * {@code null} if {@link ProjectileAttackPowerListener} never stamped this projectile (e.g.
     * it was shot before this listener was registered, or the shooter had no identifiable
     * weapon at launch) - falls back to the generic vanilla-damage path in that case. ATK%/crit/
     * element are read fresh from the shooter's *current* stats/held weapon at impact time,
     * rather than also being stamped at launch, since only the base attack power depends on
     * what was equipped at the moment of firing.
     */
    private AttackInput resolveProjectileAttack(Projectile projectile, Player shooter) {
        Double baseAttackPower = projectile.getPersistentDataContainer().get(projectileKeys.attackPower(), PersistentDataType.DOUBLE);
        if (baseAttackPower == null) {
            return null;
        }
        ItemStack weapon = shooter.getInventory().getItemInMainHand();
        WeaponData data = identityService.dataOf(weapon).orElse(null);
        StatSheet stats = statusService.getFinalStats(shooter.getUniqueId()).orElse(null);
        double atkPercent = stats != null ? stats.get(StatType.ATK) : 0;
        double critDmg = stats != null ? stats.get(StatType.CRT_DMG) : 0;
        double critRate = (data != null ? data.getCritRate() : 0.0) + (stats != null ? stats.get(StatType.CRT) : 0);
        double critMultiplier = data != null ? data.getCritMultiplier() : DamageFormula.DEFAULT_CRIT_MULTIPLIER;
        ElementType element = data != null ? data.getElement() : ElementType.NONE;
        double elementalDamageBonus = elementalDamageBonusPercentFor(element, stats);
        return new AttackInput(baseAttackPower, atkPercent, critRate, critMultiplier, critDmg, elementalDamageBonus, element);
    }

    /** Maps a weapon's element to the relic-granted {@code StatType} that boosts damage dealt with it - 0 for {@link ElementType#NONE} or no stats. */
    private double elementalDamageBonusPercentFor(ElementType element, StatSheet stats) {
        if (stats == null || element == ElementType.NONE) {
            return 0;
        }
        StatType statType = switch (element) {
            case FIRE -> StatType.FIRE_DMG;
            case WATER -> StatType.WATER_DMG;
            case EARTH -> StatType.EARTH_DMG;
            case WIND -> StatType.WIND_DMG;
            case LIGHT -> StatType.LIGHT_DMG;
            case DARK -> StatType.DARK_DMG;
            case NONE -> null;
        };
        return statType == null ? 0 : stats.get(statType);
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
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return false;
        }
        MonsterData data = spawnService.dataOf(victim).orElse(null);
        if (data == null || data.getWeakness() == ElementType.NONE) {
            return false;
        }
        ItemStack weapon = weaponOf(event.getDamager());
        if (weapon == null) {
            return false;
        }
        return identityService.dataOf(weapon)
                .map(WeaponData::getElement)
                .map(element -> element == data.getWeakness())
                .orElse(false);
    }

    /**
     * The main-hand weapon behind an attack, whether it landed as a melee swing or a shot
     * arrow - {@code event.getDamager()} is a {@link Player} for the former and a
     * {@link Projectile} for the latter, so an elemental bow's weakness bonus previously only
     * applied when meleeing with it, never when actually shooting it.
     */
    private ItemStack weaponOf(Entity damager) {
        if (damager instanceof Player attacker) {
            return attacker.getInventory().getItemInMainHand();
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter.getInventory().getItemInMainHand();
        }
        return null;
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

    /** Stamps the attack's element on the victim so {@code DamageDisplayListener} can tint the floating damage number by it. */
    private void applyElementMetadata(Entity victim, ElementType element) {
        if (victim instanceof LivingEntity livingVictim) {
            livingVictim.setMetadata(DamageFormula.ELEMENT_METADATA_KEY, new FixedMetadataValue(plugin, element.name()));
        }
    }

    private record AttackInput(double baseAttackPower, double atkPercent, double critRate, double critMultiplier,
                                double critDmgPercent, double elementalDamageBonusPercent, ElementType element) {
    }
}
