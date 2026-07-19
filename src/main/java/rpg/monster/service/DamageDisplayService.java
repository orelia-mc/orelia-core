package rpg.monster.service;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitTask;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;
import rpg.core.OreliaPlugin;
import rpg.util.ColorUtil;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Spawns a floating {@link TextDisplay} showing a damage number at {@code origin}, falling
 * under a (configurable, roughly half-vanilla-strength) gravity for a set number of ticks
 * before removing itself. Purely cosmetic combat feedback - works for any
 * {@link org.bukkit.entity.LivingEntity} taking damage, not just monsters.
 *
 * <p>Display entities (unlike mobs/items/arrows) don't participate in Minecraft's normal
 * velocity/gravity physics simulation at all - their position is purely teleport-driven, so
 * {@link org.bukkit.entity.Entity#setVelocity}/{@code setGravity} are silent no-ops here (an
 * earlier version of this class tried exactly that and the display just sat still). The fall
 * is instead simulated manually: an accumulated downward offset (increasing by
 * {@code gravity-per-tick} every tick, so it accelerates like real gravity) is applied via
 * {@link TextDisplay#teleport} each tick.
 */
public final class DamageDisplayService {

    private final OreliaPlugin plugin;

    public DamageDisplayService(OreliaPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Location origin, double amount, boolean isCrit) {
        var config = plugin.getConfigManager().get("config.yml").get();
        if (!config.getBoolean("combat.damage-display.enabled", true)) {
            return;
        }
        long durationTicks = config.getLong("combat.damage-display.duration-ticks", 20);
        double gravityPerTick = config.getDouble("combat.damage-display.gravity-per-tick", 0.02);
        double yOffset = config.getDouble("combat.damage-display.y-offset", -0.3);
        String color = config.getString(isCrit ? "combat.damage-display.crit-color" : "combat.damage-display.normal-color",
                isCrit ? "&e" : "&f");
        float scale = isCrit ? (float) config.getDouble("combat.damage-display.crit-scale", 1.3) : 1.0f;

        Location spawnLocation = origin.clone().add(0, yOffset, 0);
        TextDisplay display = spawnLocation.getWorld().spawn(spawnLocation, TextDisplay.class, d -> {
            d.text(ColorUtil.component(color + Math.round(amount)));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            if (scale != 1.0f) {
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f()));
            }
        });

        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
        long[] ticksElapsed = {0};
        double[] fallSpeed = {0.0};
        taskRef.set(plugin.getSchedulerService().runTimer(() -> {
            if (!display.isValid() || ticksElapsed[0] >= durationTicks) {
                display.remove();
                taskRef.get().cancel();
                return;
            }
            fallSpeed[0] += gravityPerTick;
            display.teleport(display.getLocation().add(0, -fallSpeed[0], 0));
            ticksElapsed[0]++;
        }, 1L, 1L));
    }
}
