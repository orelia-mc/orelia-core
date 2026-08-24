package rpg.quest.model;

/**
 * Reward bundle (SOW section 11: 経験値/お金/武器/アクセサリー/スキルポイント/称号/アイテム).
 * Every field is optional; {@code null}/zero means "not granted".
 */
public final class QuestReward {

    private final long exp;
    private final double money;
    private final String weaponId;
    private final String accessoryId;
    private final int skillPoints;
    private final String title;
    private final String vanillaMaterial;
    private final int vanillaAmount;

    public QuestReward(long exp, double money, String weaponId, String accessoryId, int skillPoints,
                        String title, String vanillaMaterial, int vanillaAmount) {
        this.exp = exp;
        this.money = money;
        this.weaponId = weaponId;
        this.accessoryId = accessoryId;
        this.skillPoints = skillPoints;
        this.title = title;
        this.vanillaMaterial = vanillaMaterial;
        this.vanillaAmount = vanillaAmount;
    }

    public long getExp() {
        return exp;
    }

    public double getMoney() {
        return money;
    }

    public String getWeaponId() {
        return weaponId;
    }

    public String getAccessoryId() {
        return accessoryId;
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public String getTitle() {
        return title;
    }

    public String getVanillaMaterial() {
        return vanillaMaterial;
    }

    public int getVanillaAmount() {
        return vanillaAmount;
    }
}
