package rpg.monster.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import rpg.item.model.ElementType;
import rpg.monster.model.AiType;
import rpg.monster.model.DropEntry;
import rpg.monster.model.MonsterData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link MonsterData}, rebuilt from {@code monsters.yml}.
 */
public final class MonsterRepository {

    private Map<String, MonsterData> monsters = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, MonsterData> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("monsters");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection monsterSection = section.getConfigurationSection(id);
                if (monsterSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, monsterSection));
            }
        }
        this.monsters = loaded;
    }

    private MonsterData parse(String id, ConfigurationSection section) {
        List<DropEntry> drops = new ArrayList<>();
        ConfigurationSection dropsSection = section.getConfigurationSection("drops");
        if (dropsSection != null) {
            for (String dropId : dropsSection.getKeys(false)) {
                ConfigurationSection dropSection = dropsSection.getConfigurationSection(dropId);
                if (dropSection == null) {
                    continue;
                }
                String weaponId = dropSection.getString("weapon-id");
                String vanillaMaterial = dropSection.getString("vanilla-material");
                drops.add(new DropEntry(
                        weaponId,
                        vanillaMaterial,
                        dropSection.getDouble("chance-percent", 10.0),
                        dropSection.getInt("min-amount", 1),
                        dropSection.getInt("max-amount", 1)));
            }
        }

        return new MonsterData(
                id,
                section.getString("name", id),
                EntityType.valueOf(section.getString("entity-type", "ZOMBIE").trim().toUpperCase()),
                section.getDouble("hp", 20.0),
                section.getDouble("attack-power", 3.0),
                section.getDouble("defense", 0.0),
                ElementType.valueOf(section.getString("element", "NONE").trim().toUpperCase()),
                ElementType.valueOf(section.getString("weakness", "NONE").trim().toUpperCase()),
                AiType.valueOf(section.getString("ai-type", "AGGRESSIVE").trim().toUpperCase()),
                drops,
                section.getLong("exp-reward", 10),
                section.getDouble("money-min", 0),
                section.getDouble("money-max", 0),
                section.getStringList("skills"));
    }

    public Optional<MonsterData> findById(String id) {
        return Optional.ofNullable(monsters.get(id));
    }

    public Map<String, MonsterData> getAll() {
        return Map.copyOf(monsters);
    }
}
