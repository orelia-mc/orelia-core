package rpg.item.listener;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import rpg.core.OreliaPlugin;
import rpg.core.message.MessageManager;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.item.service.WeaponRequirementService;
import rpg.status.combat.DamageFormula;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * The mage's three fixed wand actions (魔法の杖):
 *
 * <ul>
 *   <li>Right-click (main hand) - fires a snowball-based ice bolt at a single target.
 *   <li>Left-click (main hand) - erupts Evoker Fangs in a radius around the caster.
 *   <li>Swapping the wand into the off-hand (F by default) - opens a magic circle (魔法陣) at
 *       the caster's feet and fires lasers outward in every horizontal direction.
 * </ul>
 *
 * Unlike every other weapon type, these aren't player-chosen sockets cast through
 * {@code rpg.skill.service.SkillCastService} - they're fixed to the wand itself
 * ({@code magic_wand}'s {@code skill-slot-count} is 0, so the generic
 * {@code rpg.skill.listener.SkillActivationListener} never has anything to cast for it) - so
 * cooldown/SP gating is reimplemented here directly rather than going through
 * {@code PlayerSkillComponent}.
 *
 * <p>Damage for every action is computed manually (weapon base attack power x that action's
 * own multiplier, then the caster's ATK%) and delivered via {@code target.damage(amount,
 * caster)} under {@link DamageFormula#SKILL_OVERRIDE_METADATA}, the same convention
 * {@code rpg.skill.executor.SkillDamage} uses - so DEF mitigation, crit, and elemental weakness
 * still resolve per-target in {@code rpg.monster.listener.CombatDamageListener}. The
 * {@code EvokerFangs} summoned for the left-click ability are purely a visual/sound effect
 * (see {@link DamageFormula#WAND_FANGS_METADATA_KEY}) - their own native attack is bypassed
 * entirely rather than relied upon, since it deals a fixed vanilla amount with no ATK%/crit
 * scaling.
 *
 * <p>Every {@link PlayerInteractEvent} this class recognizes as involving a wand is cancelled
 * unconditionally, in both hands, since {@code WeaponType.WAND} is hoe-based and must never
 * till farmland or otherwise fall through to vanilla item-use behavior.
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

    private static final double MAGIC_CIRCLE_SP_COST = 20.0;
    private static final long MAGIC_CIRCLE_COOLDOWN_MILLIS = 8000L;
    private static final double LASER_DAMAGE_MULTIPLIER = 1.5;
    /** Radius of the 魔法陣 drawn at the caster's feet. */
    private static final double MAGIC_CIRCLE_RADIUS = 2.0;
    private static final int MAGIC_CIRCLE_POINTS = 48;
    /** Lifts the circle slightly off the ground so it isn't swallowed by the block below. */
    private static final double MAGIC_CIRCLE_Y_OFFSET = 0.1;
    /** Horizontal directions the lasers fan out into, evenly spaced across the full 360 degrees. */
    private static final int LASER_COUNT = 8;
    private static final double LASER_RANGE = 8.0;
    private static final double LASER_HIT_RADIUS = 0.8;
    private static final double LASER_PARTICLE_STEP = 0.5;
    private static final long LASER_ANIMATION_TICKS = 8L;
    /** Lasers leave the circle at chest height rather than at the caster's feet. */
    private static final double LASER_Y_OFFSET = 1.0;

    private final WeaponIdentityService identityService;
    private final WeaponRequirementService requirementService;
    private final StatusService statusService;
    private final MessageManager messages;
    private final OreliaPlugin plugin;

    private final Map<UUID, Long> iceBoltCooldownExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fangsCooldownExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Long> magicCircleCooldownExpiry = new ConcurrentHashMap<>();

    public MagicWandAbilityListener(WeaponIdentityService identityService, WeaponRequirementService requirementService,
                                     StatusService statusService, MessageManager messages, OreliaPlugin plugin) {
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

    /**
     * A wand sitting in the off-hand must not fall through to vanilla item use - it is hoe-based,
     * so an off-hand right-click on dirt would otherwise till farmland (the main-hand handlers
     * above only ever see {@link EquipmentSlot#HAND}). The wand's own abilities are all main-hand
     * or swap-triggered, so this handler only suppresses vanilla behavior; it never casts.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onOffHandInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (wandDataOf(event.getPlayer().getInventory().getItemInOffHand()).isPresent()) {
            event.setCancelled(true);
        }
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

    /**
     * Moving the wand into the off-hand (the swap-hands key, F by default) opens the magic
     * circle. {@link PlayerSwapHandItemsEvent} fires <em>before</em> the swap is applied, so the
     * wand is identified from {@link PlayerSwapHandItemsEvent#getOffHandItem()} - the stack that
     * is about to land in the off-hand - rather than from the player's current inventory.
     *
     * <p>Runs at {@link EventPriority#HIGH} with {@code ignoreCancelled} because
     * {@code rpg.skill.listener.SkillActivationListener} handles the same event at NORMAL and
     * cancels it whenever the swap instead cast a main-hand weapon's socketed skill; a cancelled
     * swap never puts the wand in the off-hand, so nothing should be cast here either.
     *
     * <p>A failed cooldown/SP check deliberately does <em>not</em> cancel the event - blocking
     * the swap itself would leave the player unable to move the wand between hands at all.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapToOffHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack incoming = event.getOffHandItem();
        WeaponData data = wandDataOf(incoming).orElse(null);
        if (data == null) {
            return;
        }
        if (!checkAndConsume(player, data, magicCircleCooldownExpiry,
                MAGIC_CIRCLE_COOLDOWN_MILLIS, MAGIC_CIRCLE_SP_COST)) {
            return;
        }
        double amount = computeDamage(player, incoming, data, LASER_DAMAGE_MULTIPLIER);
        castMagicCircle(player, amount);
    }

    /**
     * Draws the magic circle at the caster's feet and extends {@link #LASER_COUNT} lasers outward
     * from it over {@link #LASER_ANIMATION_TICKS} ticks, damaging each entity the beams sweep
     * through exactly once.
     *
     * <p>The origin is captured (and cloned) at cast time, so the circle stays where it was
     * opened even if the caster walks away mid-animation. {@code alreadyHit} is an
     * identity-based set held across every tick of the animation, so an entity standing where
     * two beams overlap - or one that a beam passes through over several ticks - still only
     * takes damage once.
     *
     * <p>Follows {@code rpg.monster.service.DamageDisplayService}'s self-cancelling timer idiom
     * ({@link AtomicReference} holding the task so the lambda can cancel itself); the animation
     * also stops early if the caster logs out.
     */
    private void castMagicCircle(Player caster, double amount) {
        Location origin = caster.getLocation().clone();
        Set<LivingEntity> alreadyHit = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean[] blocked = new boolean[LASER_COUNT];
        long[] ticksElapsed = {0};
        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();

        origin.getWorld().playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);

        taskRef.set(plugin.getSchedulerService().runTimer(() -> {
            if (!caster.isOnline() || ticksElapsed[0] >= LASER_ANIMATION_TICKS) {
                taskRef.get().cancel();
                return;
            }
            drawMagicCircle(origin);
            double from = LASER_RANGE * ticksElapsed[0] / LASER_ANIMATION_TICKS;
            double to = LASER_RANGE * (ticksElapsed[0] + 1) / LASER_ANIMATION_TICKS;
            advanceLasers(caster, origin, from, to, amount, alreadyHit, blocked);
            ticksElapsed[0]++;
        }, 1L, 1L));
    }

    /** One ring of particles on the ground, redrawn every animation tick so the circle stays visible. */
    private void drawMagicCircle(Location origin) {
        Location center = origin.clone().add(0, MAGIC_CIRCLE_Y_OFFSET, 0);
        for (int i = 0; i < MAGIC_CIRCLE_POINTS; i++) {
            double angle = 2 * Math.PI * i / MAGIC_CIRCLE_POINTS;
            Location point = center.clone().add(Math.cos(angle) * MAGIC_CIRCLE_RADIUS, 0,
                    Math.sin(angle) * MAGIC_CIRCLE_RADIUS);
            center.getWorld().spawnParticle(Particle.ENCHANT, point, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Advances every not-yet-blocked laser through the {@code from}..{@code to} band of its
     * range, drawing it and damaging whatever it sweeps through. A beam that reaches a solid
     * block stops there permanently ({@code blocked[i]}) so lasers never shoot through walls.
     */
    private void advanceLasers(Player caster, Location origin, double from, double to, double amount,
                                Set<LivingEntity> alreadyHit, boolean[] blocked) {
        Location start = origin.clone().add(0, LASER_Y_OFFSET, 0);
        for (int i = 0; i < LASER_COUNT; i++) {
            if (blocked[i]) {
                continue;
            }
            double angle = 2 * Math.PI * i / LASER_COUNT;
            Vector direction = new Vector(Math.cos(angle), 0, Math.sin(angle));
            for (double distance = from; distance < to; distance += LASER_PARTICLE_STEP) {
                Location point = start.clone().add(direction.clone().multiply(distance));
                Block block = point.getBlock();
                if (!block.isPassable()) {
                    blocked[i] = true;
                    break;
                }
                point.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                for (LivingEntity target : point.getWorld().getNearbyLivingEntities(point,
                        LASER_HIT_RADIUS, LASER_HIT_RADIUS, LASER_HIT_RADIUS)) {
                    if (isValidTarget(caster, target) && alreadyHit.add(target)) {
                        applyHit(caster, target, amount);
                    }
                }
            }
        }
    }

    private Optional<WeaponData> wandDataOf(ItemStack stack) {
        return identityService.dataOf(stack).filter(data -> data.getWeaponType() == WeaponType.WAND);
    }

    private Optional<WeaponData> weaponIfMage(Player player) {
        return wandDataOf(player.getInventory().getItemInMainHand());
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

    /**
     * Never the caster, and never another player while the world has PvP off - these abilities
     * deliver damage through a direct {@code Entity#damage} call rather than the vanilla attack
     * path, which is what normally enforces the world's PvP flag. Mirrors
     * {@code rpg.skill.executor.TargetFinder#isValidTarget}.
     */
    private boolean isValidTarget(Player caster, Entity entity) {
        return entity != caster && (!(entity instanceof Player) || caster.getWorld().getPVP());
    }

    /** Living entities within {@code radius} blocks of the caster, excluding the caster - mirrors {@code rpg.skill.executor.TargetFinder#inRadius}. */
    private List<LivingEntity> nearbyTargets(Player caster, double radius) {
        return caster.getWorld().getNearbyLivingEntities(caster.getLocation(), radius, radius, radius,
                entity -> isValidTarget(caster, entity)).stream().collect(Collectors.toList());
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
