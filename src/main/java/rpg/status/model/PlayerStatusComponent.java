package rpg.status.model;

import rpg.core.player.PlayerDataComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-player runtime status state: level/experience, the persisted base stat sheet,
 * live equipment contributions (keyed by source, e.g. {@code "weapon"}, {@code "accessory:ring"}),
 * active timed buffs, and current HP/SP.
 */
public final class PlayerStatusComponent implements PlayerDataComponent {

    private final UUID owner;
    private int level;
    private long experience;
    private StatSheet baseStats;
    private final Map<String, StatSheet> equipmentContributions = new ConcurrentHashMap<>();
    private final List<StatModifier> buffs = new CopyOnWriteArrayList<>();
    private double currentHp;
    private double currentSp;

    public PlayerStatusComponent(UUID owner, int level, long experience, StatSheet baseStats, double currentHp, double currentSp) {
        this.owner = owner;
        this.level = level;
        this.experience = experience;
        this.baseStats = baseStats;
        this.currentHp = currentHp;
        this.currentSp = currentSp;
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getExperience() {
        return experience;
    }

    public void setExperience(long experience) {
        this.experience = experience;
    }

    public StatSheet getBaseStats() {
        return baseStats;
    }

    public void setBaseStats(StatSheet baseStats) {
        this.baseStats = baseStats;
    }

    public void setEquipmentContribution(String sourceKey, StatSheet sheet) {
        equipmentContributions.put(sourceKey, sheet);
    }

    public void clearEquipmentContribution(String sourceKey) {
        equipmentContributions.remove(sourceKey);
    }

    public List<StatSheet> getEquipmentContributions() {
        return List.copyOf(equipmentContributions.values());
    }

    public void addBuff(StatModifier modifier) {
        buffs.add(modifier);
    }

    public void removeExpiredBuffs(long nowMillis) {
        buffs.removeIf(modifier -> modifier.isExpired(nowMillis));
    }

    public void removeBuffsFromSource(String sourceKey) {
        buffs.removeIf(modifier -> modifier.getSourceKey().equals(sourceKey));
    }

    public List<StatModifier> getBuffs() {
        return new ArrayList<>(buffs);
    }

    public double getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(double currentHp) {
        this.currentHp = currentHp;
    }

    public double getCurrentSp() {
        return currentSp;
    }

    public void setCurrentSp(double currentSp) {
        this.currentSp = currentSp;
    }
}
