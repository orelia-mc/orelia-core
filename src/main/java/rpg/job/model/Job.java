package rpg.job.model;

import rpg.item.model.WeaponType;
import rpg.status.model.StatSheet;

import java.util.Set;

/**
 * Static definition of one job: which weapon types it may equip and its passive stat
 * bonus, loaded from {@code jobs.yml}.
 */
public final class Job {

    private final JobType type;
    private final String displayName;
    private final Set<WeaponType> allowedWeapons;
    private final StatSheet passiveBonus;

    public Job(JobType type, String displayName, Set<WeaponType> allowedWeapons, StatSheet passiveBonus) {
        this.type = type;
        this.displayName = displayName;
        this.allowedWeapons = allowedWeapons;
        this.passiveBonus = passiveBonus;
    }

    public JobType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean canUse(WeaponType weaponType) {
        return allowedWeapons.contains(weaponType);
    }

    public StatSheet getPassiveBonus() {
        return passiveBonus;
    }
}
