package rpg.monster.model;

import org.bukkit.entity.EntityType;
import rpg.item.model.ElementType;

import java.util.List;

/**
 * Static monster definition loaded from {@code monsters.yml} (SOW section 13).
 * {@code skillIds} is a hook for future monster ability AI (not yet executed by the base
 * monster module - {@link rpg.boss.model.BossData} is where scripted attacks land first).
 */
public final class MonsterData {

    private final String id;
    private final String name;
    private final EntityType entityType;
    private final double hp;
    private final double attackPower;
    private final double defense;
    private final ElementType element;
    private final ElementType weakness;
    private final AiType aiType;
    private final List<DropEntry> drops;
    private final long expReward;
    private final double moneyMin;
    private final double moneyMax;
    private final List<String> skillIds;

    public MonsterData(String id, String name, EntityType entityType, double hp, double attackPower, double defense,
                        ElementType element, ElementType weakness, AiType aiType, List<DropEntry> drops, long expReward,
                        double moneyMin, double moneyMax, List<String> skillIds) {
        this.id = id;
        this.name = name;
        this.entityType = entityType;
        this.hp = hp;
        this.attackPower = attackPower;
        this.defense = defense;
        this.element = element;
        this.weakness = weakness;
        this.aiType = aiType;
        this.drops = drops;
        this.expReward = expReward;
        this.moneyMin = moneyMin;
        this.moneyMax = moneyMax;
        this.skillIds = skillIds;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public double getHp() {
        return hp;
    }

    public double getAttackPower() {
        return attackPower;
    }

    public double getDefense() {
        return defense;
    }

    public ElementType getElement() {
        return element;
    }

    /** Element a weapon must carry to deal {@code MonsterCombatListener}'s weakness-multiplier damage; {@code NONE} means no weak point. */
    public ElementType getWeakness() {
        return weakness;
    }

    public AiType getAiType() {
        return aiType;
    }

    public List<DropEntry> getDrops() {
        return drops;
    }

    public long getExpReward() {
        return expReward;
    }

    public double getMoneyMin() {
        return moneyMin;
    }

    public double getMoneyMax() {
        return moneyMax;
    }

    public List<String> getSkillIds() {
        return skillIds;
    }
}
