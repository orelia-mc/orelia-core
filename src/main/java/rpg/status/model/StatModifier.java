package rpg.status.model;

import java.util.UUID;

/**
 * A single timed buff/debuff contribution (e.g. from a necklace's SP regen, a skill's
 * self-buff, or a boss's enrage debuff). {@code expiresAtMillis <= 0} means permanent
 * for the lifetime of its source (removed explicitly, not by timer).
 */
public final class StatModifier {

    private final UUID id;
    private final String sourceKey;
    private final StatType statType;
    private final ModifierType modifierType;
    private final double amount;
    private final long expiresAtMillis;

    public StatModifier(String sourceKey, StatType statType, ModifierType modifierType, double amount, long expiresAtMillis) {
        this.id = UUID.randomUUID();
        this.sourceKey = sourceKey;
        this.statType = statType;
        this.modifierType = modifierType;
        this.amount = amount;
        this.expiresAtMillis = expiresAtMillis;
    }

    public UUID getId() {
        return id;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public StatType getStatType() {
        return statType;
    }

    public ModifierType getModifierType() {
        return modifierType;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isExpired(long nowMillis) {
        return expiresAtMillis > 0 && nowMillis >= expiresAtMillis;
    }
}
