package rpg.item.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.item.model.ElementType;
import rpg.item.model.Rarity;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.job.model.JobType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link WeaponData}, rebuilt from {@code items.yml} on load
 * and on {@code /oladmin reload}. This is the SOW's "add a weapon via config only" contract -
 * no code change is needed to add a new entry under {@code weapons:} in items.yml.
 */
public final class WeaponRepository {

    private Map<String, WeaponData> weapons = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, WeaponData> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("weapons");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection weaponSection = section.getConfigurationSection(id);
                if (weaponSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, weaponSection));
            }
        }
        this.weapons = loaded;
    }

    private WeaponData parse(String id, ConfigurationSection section) {
        String requiredJobRaw = section.getString("required-job", "");
        JobType requiredJob = requiredJobRaw.isBlank() ? null : JobType.valueOf(requiredJobRaw.trim().toUpperCase());

        return new WeaponData(
                id,
                section.getString("name", id),
                WeaponType.valueOf(section.getString("weapon-type", "SWORD").trim().toUpperCase()),
                section.getInt("level", 1),
                Rarity.valueOf(section.getString("rarity", "COMMON").trim().toUpperCase()),
                section.getDouble("attack-power", 1.0),
                ElementType.valueOf(section.getString("element", "NONE").trim().toUpperCase()),
                section.getDouble("crit-rate", 5.0),
                section.getDouble("crit-multiplier", 1.5),
                requiredJob,
                section.getInt("required-level", 1),
                section.getStringList("description"),
                section.getInt("custom-model-data", 0),
                section.getDouble("sell-price", 0.0),
                section.getInt("skill-slot-count", 1),
                section.getBoolean("unbreakable", false),
                section.getInt("bulk-chop-radius", 0),
                section.getInt("gather-required-level", 0));
    }

    public Optional<WeaponData> findById(String id) {
        return Optional.ofNullable(weapons.get(id));
    }

    public Map<String, WeaponData> getAll() {
        return Map.copyOf(weapons);
    }
}
