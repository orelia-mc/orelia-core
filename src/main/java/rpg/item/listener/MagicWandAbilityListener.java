package rpg.item.listener;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import rpg.core.message.MessageManager;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.item.service.WeaponRequirementService;
import rpg.status.combat.DamageFormula;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * The mage's two fixed wand actions (魔法の杖) - right-click fires a snowball-based ice bolt at
 * a single target, left-click erupts Evoker Fangs in a radius around the caster (excluding the
 * caster). Unlike every other weapon type, these aren't player-chosen sockets cast through
 * {@code rpg.skill.service.SkillCastService} - they're fixed to the wand itself
 * ({@code magic_wand}'s {@code skill-slot-count} is 0, so the generic
 * {@code rpg.skill.listener.SkillActivationListener} never has anything to cast for it) - so
 * cooldown/SP gating is reimplemented here directly rather than going through
 * {@code PlayerSkillComponent}.
 *
 * <p>Damage for both actions is computed manually (weapon base attack power x this ability's
 * own multiplier, then the caster's ATK%) and delivered via {@code target.damage(amount,
 * caster)} under {@link DamageFormula#SKILL_OVERRIDE_METADATA}, the same convention
 * {@code rpg.skill.executor.SkillDamage} uses - so DEF mitigation, crit, and elemental weakness
 * still resolve per-target in {@code rpg.monster.listener.CombatDamageListener}. The
 * {@code EvokerFangs} summoned for the left-click ability are purely a visual/sound effect
 * (see {@link DamageFormula#WAND_FANGS_METADATA_KEY}) - their own native attack is bypassed
 * entirely rather than relied upon, since it deals a fixed vanilla amount with no ATK%/crit
 * scaling.
 *
 * <p>Both actions cancel the underlying {@link PlayerInteractEvent} unconditionally once the
 * wand is identified, since {@code WeaponType.WAND} is hoe-based and must never till farmland
 * or otherwise fall through to vanilla item-use behavior.
 */
public final class MagicWandAbilityListener implements Listener {

    private static final String ICE_BOLT_METADATA = "orelia_wand_ice_bolt_damage";
    private static final double ICE_BOLT_SP_COST = 8.0;
    private static final long ICE_BOLT_COOLDOWN_MILLIS = 2000L;
    private static final double ICE_BOLT_DAMAGE_MULTIPLIER = 1.3;

    private static final double FANGS_SP_COST = 14.0;
    private static final long FANGS_COOLDOWN_MILLIS = 5000L;
    private static final double FANGS_DAMAGE_MULTIPLIER = 1.2;
    private static final double FANGS_RADIUS = 2.0;
    private static final int FANGS_VISUAL_COUNT = 3;

    private final WeaponIdentityService identityService;
    private final WeaponRequirementService requirementService;
    private final StatusService statusService;
    private final MessageManager messages;
    private final Plugin plugin;

    private final Map<UUID, Long> iceBoltCooldownExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fangsCooldownExpiry = new ConcurrentHashMap<>();

    public MagicWandAbilityListener(WeaponIdentityService identityService, WeaponRequirementService requirementService,
                                     StatusService statusService, MessageManager messages, Plugin plugin) {
        this.identityService = identityService;
        this.requirementService = requirementService;
        this.statusService = statusService;
        this.messages = messages;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        WeaponData data = weaponIfMage(player).orElse(null);
        if (data == null) {
            return;
        }
        // Unconditional: WeaponType.WAND is hoe-based and must never till farmland.
        event.setCancelled(true);
        if (!checkAndConsume(player, data, iceBoltCooldownExpiry, ICE_BOLT_COOLDOWN_MILLIS, ICE_BOLT_SP_COST)) {
            return;
        }
        ItemStack wand = player.getInventory().getItemInMainHand();
        double amount = computeDamage(player, wand, data, ICE_BOLT_DAMAGE_MULTIPLIER);

        Snowball snowball = player.launchProjectile(Snowball.class, player.getLocation().getDirection().multiply(1.5));
        snowball.setMetadata(ICE_BOLT_METADATA, new FixedMetadataValue(plugin, amount));
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getEyeLocation(), 10, 0.2, 0.2, 0.2, 0.01);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1f, 1.4f);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !snowball.hasMetadata(ICE_BOLT_METADATA)) {
            return;
        }
        double amount = snowball.getMetadata(ICE_BOLT_METADATA).get(0).asDouble();
        Location impact = snowball.getLocation();
        impact.getWorld().spawnParticle(Particle.SNOWFLAKE, impact, 20, 0.3, 0.3, 0.3, 0.02);
        impact.getWorld().playSound(impact, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);

        ProjectileSource shooter = snowball.getShooter();
        if (shooter instanceof Player caster && event.getHitEntity() instanceof LivingEntity target) {
            applyHit(caster, target, amount);
        }
        snowball.remove();
    }

    /**
     * {@link PlayerInteractEvent}'s LEFT_CLICK_AIR/LEFT_CLICK_BLOCK only fires when swinging at
     * nothing or at a block, never when the swing directly lands on an entity (that instead
     * fires {@code EntityDamageByEntityEvent}, a known Bukkit/Paper limitation) - acceptable
     * here since this is a self-centered radius-2 AOE, so swinging near (not necessarily
     * directly at) nearby enemies is enough to hit them.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        WeaponData data = weaponIfMage(player).orElse(null);
        if (data == null) {
            return;
        }
        event.setCancelled(true);
        if (!checkAndConsume(player, data, fangsCooldownExpiry, FANGS_COOLDOWN_MILLIS, FANGS_SP_COST)) {
            return;
        }
        ItemStack wand = player.getInventory().getItemInMainHand();
        double amount = computeDamage(player, wand, data, FANGS_DAMAGE_MULTIPLIER);
        for (LivingEntity target : nearbyTargets(player, FANGS_RADIUS)) {
            applyHit(player, target, amount);
        }
        spawnFangsVisual(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 1f);
    }

    private Optional<WeaponData> weaponIfMage(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        return identityService.dataOf(mainHand).filter(data -> data.getWeaponType() == WeaponType.WAND);
    }

    private boolean checkAndConsume(Player player, WeaponData data, Map<UUID, Long> cooldowns, long cooldownMillis, double spCost) {
        UUID uuid = player.getUniqueId();
        if (!requirementService.meetsRequirements(uuid, data)) {
            messages.send(player, "item.requirement-not-met");
            return false;
        }
        Long expiry = cooldowns.get(uuid);
        if (expiry != null && expiry > System.currentTimeMillis()) {
            messages.send(player, "skill.on-cooldown");
            return false;
        }
        if (!statusService.tryConsumeSp(uuid, spCost)) {
            messages.send(player, "skill.not-enough-sp");
            return false;
        }
        cooldowns.put(uuid, System.currentTimeMillis() + cooldownMillis);
        return true;
    }

    private double computeDamage(Player caster, ItemStack stack, WeaponData data, double multiplier) {
        double base = identityService.baseAttackPower(stack, data) * multiplier;
        StatSheet stats = statusService.getFinalStats(caster.getUniqueId()).orElse(null);
        double atkPercent = stats != null ? stats.get(StatType.ATK) : 0;
        return DamageFormula.applyAttackBonus(base, atkPercent);
    }

    private void applyHit(Player caster, LivingEntity target, double amount) {
        caster.setMetadata(DamageFormula.SKILL_OVERRIDE_METADATA, new FixedMetadataValue(plugin, true));
        try {
            target.damage(amount, caster);
        } finally {
            caster.removeMetadata(DamageFormula.SKILL_OVERRIDE_METADATA, plugin);
        }
    }

    /** Living entities within {@code radius} blocks of the caster, excluding the caster - mirrors {@code rpg.skill.executor.TargetFinder#inRadius}. */
    private List<LivingEntity> nearbyTargets(Player caster, double radius) {
        return caster.getWorld().getNearbyLivingEntities(caster.getLocation(), radius, radius, radius, entity ->
                entity != caster && (!(entity instanceof Player) || caster.getWorld().getPVP())).stream().collect(Collectors.toList());
    }

    private void spawnFangsVisual(Player caster) {
        for (int i = 0; i < FANGS_VISUAL_COUNT; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double distance = ThreadLocalRandom.current().nextDouble(0.5, FANGS_RADIUS);
            Location location = caster.getLocation().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            EvokerFangs fangs = caster.getWorld().spawn(location, EvokerFangs.class);
            fangs.setOwner(caster);
            fangs.setMetadata(DamageFormula.WAND_FANGS_METADATA_KEY, new FixedMetadataValue(plugin, true));
        }
    }
}
