package rpg.status.service;

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
        statusComponent(uuid).ifPresent(component -> component.setEquipmentContribution(sourceKey, sheet));
    }

    public void clearEquipmentContribution(UUID uuid, String sourceKey) {
        statusComponent(uuid).ifPresent(component -> component.clearEquipmentContribution(sourceKey));
    }

    public void addBuff(UUID uuid, String sourceKey, StatType statType, ModifierType modifierType, double amount, long durationMillis) {
        statusComponent(uuid).ifPresent(component -> {
            long expiresAt = durationMillis <= 0 ? 0 : System.currentTimeMillis() + durationMillis;
            component.addBuff(new StatModifier(sourceKey, statType, modifierType, amount, expiresAt));
        });
    }

    public void removeBuffsFromSource(UUID uuid, String sourceKey) {
        statusComponent(uuid).ifPresent(component -> component.removeBuffsFromSource(sourceKey));
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

    public void damage(UUID uuid, double amount) {
        statusComponent(uuid).ifPresent(component ->
                component.setCurrentHp(Math.max(0, component.getCurrentHp() - amount)));
    }

    public void heal(UUID uuid, double amount) {
        statusComponent(uuid).ifPresent(component -> {
            double max = calculatorService.calculateFinal(component).get(StatType.HP);
            component.setCurrentHp(MathUtil.clamp(component.getCurrentHp() + amount, 0, max));
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
        });
    }

    /**
     * Adds experience and applies as many level-ups as the new total allows (capped at
     * {@link LevelingConfig#getMaxLevel()}). On each level-up, base stats are recalculated
     * from {@link LevelGrowthService} and HP/SP are refilled to the new max.
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
                component.setBaseStats(levelGrowthService.baseStatsForLevel(component.getLevel()));
                StatSheet finalStats = calculatorService.calculateFinal(component);
                component.setCurrentHp(finalStats.get(StatType.HP));
                component.setCurrentSp(finalStats.get(StatType.SP));
            }
        });
    }

    private Optional<PlayerStatusComponent> statusComponent(UUID uuid) {
        return playerDataManager.get(uuid).flatMap(data -> data.component(PlayerStatusComponent.class));
    }
}
