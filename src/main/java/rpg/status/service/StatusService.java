package rpg.status.service;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import rpg.core.player.PlayerDataManager;
import rpg.status.config.LevelingConfig;
import rpg.status.model.LeaderboardEntry;
import rpg.status.model.ModifierType;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.model.StatModifier;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.repository.StatusRepository;
import rpg.util.MathUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point other modules use to read/modify a player's status. Wraps the
 * component lookup so callers never touch {@link PlayerDataManager} directly.
 *
 * <p>{@code currentHp} is the player's "scaled" HP - it can be in the hundreds/thousands
 * depending on level/gear, while the player's real vanilla health stays fixed at (or near) 20
 * hearts. Every method here that changes {@code currentHp} outside the combat-event pipeline
 * (which handles its own vanilla sync via {@link ScaledHealthService#convertDamageToVanilla} -
 * see {@code rpg.monster.listener.CombatDamageListener}) re-syncs the online player's vanilla
 * health to match the new percentage via {@link ScaledHealthService#syncVanillaHealth}.
 */
public final class StatusService {

    private final PlayerDataManager playerDataManager;
    private final StatusCalculatorService calculatorService;
    private final LevelGrowthService levelGrowthService;
    private final LevelingConfig levelingConfig;
    private final StatusRepository repository;

    public StatusService(PlayerDataManager playerDataManager, StatusCalculatorService calculatorService,
                          LevelGrowthService levelGrowthService, LevelingConfig levelingConfig, StatusRepository repository) {
        this.playerDataManager = playerDataManager;
        this.calculatorService = calculatorService;
        this.levelGrowthService = levelGrowthService;
        this.levelingConfig = levelingConfig;
        this.repository = repository;
    }

    /** Top players by level, straight from storage (SOW RankingModule) - includes offline players. */
    public List<LeaderboardEntry> getLeaderboard(int limit) {
        return repository.findTopByLevel(limit);
    }

    public Optional<PlayerStatusComponent> component(UUID uuid) {
        return statusComponent(uuid);
    }

    public Optional<StatSheet> getFinalStats(UUID uuid) {
        return statusComponent(uuid).map(calculatorService::calculateFinal);
    }

    public void setEquipmentContribution(UUID uuid, String sourceKey, StatSheet sheet) {
        statusComponent(uuid).ifPresent(component -> {
            double oldMax = calculatorService.calculateFinal(component).get(StatType.HP);
            component.setEquipmentContribution(sourceKey, sheet);
            reconcileScaledHealth(uuid, component, oldMax);
        });
    }

    public void clearEquipmentContribution(UUID uuid, String sourceKey) {
        statusComponent(uuid).ifPresent(component -> {
            double oldMax = calculatorService.calculateFinal(component).get(StatType.HP);
            component.clearEquipmentContribution(sourceKey);
            reconcileScaledHealth(uuid, component, oldMax);
        });
    }

    public void addBuff(UUID uuid, String sourceKey, StatType statType, ModifierType modifierType, double amount, long durationMillis) {
        statusComponent(uuid).ifPresent(component -> {
            double oldMax = calculatorService.calculateFinal(component).get(StatType.HP);
            long expiresAt = durationMillis <= 0 ? 0 : System.currentTimeMillis() + durationMillis;
            component.addBuff(new StatModifier(sourceKey, statType, modifierType, amount, expiresAt));
            reconcileScaledHealth(uuid, component, oldMax);
        });
    }

    public void removeBuffsFromSource(UUID uuid, String sourceKey) {
        statusComponent(uuid).ifPresent(component -> {
            double oldMax = calculatorService.calculateFinal(component).get(StatType.HP);
            component.removeBuffsFromSource(sourceKey);
            reconcileScaledHealth(uuid, component, oldMax);
        });
    }

    public boolean tryConsumeSp(UUID uuid, double amount) {
        Optional<PlayerStatusComponent> componentOpt = statusComponent(uuid);
        if (componentOpt.isEmpty()) {
            return false;
        }
        PlayerStatusComponent component = componentOpt.get();
        if (component.getCurrentSp() < amount) {
            return false;
        }
        component.setCurrentSp(component.getCurrentSp() - amount);
        return true;
    }

    /** For callers outside the Bukkit damage-event pipeline (skills, API, quest effects, ...) - syncs vanilla health too. */
    public void damage(UUID uuid, double amount) {
        statusComponent(uuid).ifPresent(component -> {
            double max = calculatorService.calculateFinal(component).get(StatType.HP);
            component.setCurrentHp(Math.max(0, component.getCurrentHp() - amount));
            syncVanillaHealth(uuid, component.getCurrentHp(), max);
        });
    }

    public void heal(UUID uuid, double amount) {
        statusComponent(uuid).ifPresent(component -> {
            double max = calculatorService.calculateFinal(component).get(StatType.HP);
            component.setCurrentHp(MathUtil.clamp(component.getCurrentHp() + amount, 0, max));
            syncVanillaHealth(uuid, component.getCurrentHp(), max);
        });
    }

    /**
     * Reduces {@code currentHp} by a scaled damage amount already computed by
     * {@code CombatDamageListener} - deliberately does NOT touch vanilla health, since that
     * listener converts the same amount into a vanilla-equivalent value for
     * {@code EntityDamageEvent#setDamage} instead, letting Bukkit's own event resolution
     * (knockback, hurt sound, death) apply it naturally.
     */
    public void applyScaledCombatDamage(UUID uuid, double scaledAmount) {
        statusComponent(uuid).ifPresent(component ->
                component.setCurrentHp(Math.max(0, component.getCurrentHp() - scaledAmount)));
    }

    /**
     * Reduces {@code currentHp} for damage that never passed through the combat event
     * pipeline - fall/fire/drowning/... (see
     * {@code rpg.status.listener.ScaledHealthEnvironmentalDamageListener}) - by mirroring the
     * same vanilla percentage lost onto the scaled side. Deliberately does NOT touch vanilla
     * health, since Bukkit already applied {@code vanillaAmount} to it naturally; without this,
     * the next thing that re-syncs vanilla health from the (unchanged) scaled {@code currentHp}
     * would restore it back up, looking like an instant heal after fall damage.
     */
    public void applyEnvironmentalDamage(UUID uuid, double vanillaAmount) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double vanillaMax = attribute != null ? attribute.getValue() : player.getHealth();
        if (vanillaMax <= 0) {
            return;
        }
        statusComponent(uuid).ifPresent(component -> {
            double scaledMax = calculatorService.calculateFinal(component).get(StatType.HP);
            if (scaledMax <= 0) {
                return;
            }
            double scaledDamage = (vanillaAmount / vanillaMax) * scaledMax;
            component.setCurrentHp(Math.max(0, component.getCurrentHp() - scaledDamage));
        });
    }

    /**
     * Regenerates HP/SP by the given percentage of max per tick and prunes expired buffs.
     * Called periodically by {@link rpg.status.StatusModule}.
     */
    public void tickRegen(UUID uuid, double hpRegenPercent, double spRegenPercent) {
        statusComponent(uuid).ifPresent(component -> {
            component.removeExpiredBuffs(System.currentTimeMillis());
            StatSheet finalStats = calculatorService.calculateFinal(component);
            double maxHp = finalStats.get(StatType.HP);
            double maxSp = finalStats.get(StatType.SP);
            component.setCurrentHp(MathUtil.clamp(component.getCurrentHp() + maxHp * hpRegenPercent / 100.0, 0, maxHp));
            component.setCurrentSp(MathUtil.clamp(component.getCurrentSp() + maxSp * spRegenPercent / 100.0, 0, maxSp));
            syncVanillaHealth(uuid, component.getCurrentHp(), maxHp);
        });
    }

    /**
     * Adds experience and applies as many level-ups as the new total allows (capped at
     * {@link LevelingConfig#getMaxLevel()}). On each level-up, base stats are recalculated
     * from {@link LevelGrowthService} and HP/SP keep the same percentage of the new max they
     * held of the old max, rather than being refilled to full - see {@link #reconcileScaledHealth}.
     */
    public void addExperience(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        statusComponent(uuid).ifPresent(component -> {
            component.setExperience(component.getExperience() + amount);
            boolean leveledUp = false;
            while (component.getLevel() < levelingConfig.getMaxLevel()
                    && component.getExperience() >= levelingConfig.requiredExperience(component.getLevel())) {
                component.setExperience(component.getExperience() - levelingConfig.requiredExperience(component.getLevel()));
                component.setLevel(component.getLevel() + 1);
                leveledUp = true;
            }
            if (leveledUp) {
                StatSheet oldStats = calculatorService.calculateFinal(component);
                double oldHpFraction = fractionOf(component.getCurrentHp(), oldStats.get(StatType.HP));
                double oldSpFraction = fractionOf(component.getCurrentSp(), oldStats.get(StatType.SP));

                component.setBaseStats(levelGrowthService.baseStatsForLevel(component.getLevel()));
                StatSheet finalStats = calculatorService.calculateFinal(component);
                component.setCurrentHp(oldHpFraction * finalStats.get(StatType.HP));
                component.setCurrentSp(oldSpFraction * finalStats.get(StatType.SP));
                syncVanillaHealth(uuid, component.getCurrentHp(), finalStats.get(StatType.HP));
            }
        });
    }

    /**
     * Re-syncs vanilla health after something that can change max HP without changing
     * {@code currentHp} itself (equipment/buff changes) - preserves {@code currentHp}'s
     * percentage of {@code oldMax} (captured by the caller before mutating the
     * equipment/buff state that feeds {@code calculateFinal}) onto the new max, e.g. 50% HP
     * stays ~50% HP after a max-HP change, rather than clamping the old absolute value into
     * the new range.
     */
    private void reconcileScaledHealth(UUID uuid, PlayerStatusComponent component, double oldMax) {
        double oldFraction = fractionOf(component.getCurrentHp(), oldMax);
        double newMax = calculatorService.calculateFinal(component).get(StatType.HP);
        component.setCurrentHp(MathUtil.clamp(oldFraction * newMax, 0, newMax));
        syncVanillaHealth(uuid, component.getCurrentHp(), newMax);
    }

    /** {@code current / max}, clamped to [0, 1] and defaulting to 1 (full) when {@code max} is non-positive. */
    private double fractionOf(double current, double max) {
        if (max <= 0) {
            return 1.0;
        }
        return MathUtil.clamp(current / max, 0, 1);
    }

    private void syncVanillaHealth(UUID uuid, double currentHp, double maxHp) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            ScaledHealthService.syncVanillaHealth(player, currentHp, maxHp);
        }
    }

    private Optional<PlayerStatusComponent> statusComponent(UUID uuid) {
        return playerDataManager.get(uuid).flatMap(data -> data.component(PlayerStatusComponent.class));
    }
}
