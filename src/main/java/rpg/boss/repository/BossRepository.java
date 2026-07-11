package rpg.boss.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.boss.model.BossAbility;
import rpg.boss.model.BossAbilityType;
import rpg.boss.model.BossData;
import rpg.boss.model.BossPhase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link BossData}, rebuilt from {@code bosses.yml}.
 */
public final class BossRepository {

    private Map<String, BossData> bosses = new LinkedHashMap<>();
    private Map<String, BossData> byMonsterId = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, BossData> loaded = new LinkedHashMap<>();
        Map<String, BossData> loadedByMonster = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("bosses");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection bossSection = section.getConfigurationSection(id);
                if (bossSection == null) {
                    continue;
                }
                BossData data = parse(id, bossSection);
                loaded.put(id, data);
                loadedByMonster.put(data.getMonsterId(), data);
            }
        }
        this.bosses = loaded;
        this.byMonsterId = loadedByMonster;
    }

    private BossData parse(String id, ConfigurationSection section) {
        List<BossPhase> phases = new ArrayList<>();
        ConfigurationSection phasesSection = section.getConfigurationSection("phases");
        if (phasesSection != null) {
            for (String phaseId : phasesSection.getKeys(false)) {
                ConfigurationSection phaseSection = phasesSection.getConfigurationSection(phaseId);
                if (phaseSection == null) {
                    continue;
                }
                phases.add(new BossPhase(
                        phaseSection.getDouble("hp-threshold-percent", 50.0),
                        phaseSection.getString("announce-message", "")));
            }
        }
        phases.sort(Comparator.comparingDouble(BossPhase::getHpThresholdPercent).reversed());

        List<BossAbility> abilities = new ArrayList<>();
        ConfigurationSection abilitiesSection = section.getConfigurationSection("abilities");
        if (abilitiesSection != null) {
            for (String abilityId : abilitiesSection.getKeys(false)) {
                ConfigurationSection abilitySection = abilitiesSection.getConfigurationSection(abilityId);
                if (abilitySection == null) {
                    continue;
                }
                abilities.add(new BossAbility(
                        abilityId,
                        abilitySection.getString("name", abilityId),
                        BossAbilityType.valueOf(abilitySection.getString("type", "AOE_SLAM").trim().toUpperCase()),
                        abilitySection.getDouble("damage", 10.0),
                        abilitySection.getDouble("radius", 5.0),
                        abilitySection.getInt("cooldown-seconds", 15),
                        abilitySection.getString("particle", "EXPLOSION_EMITTER"),
                        abilitySection.getString("sound", "ENTITY_WITHER_SHOOT"),
                        abilitySection.getString("announce-message", "")));
            }
        }

        return new BossData(
                id,
                section.getString("monster-id", id),
                phases,
                section.getDouble("enrage-hp-percent", 20.0),
                section.getDouble("enrage-damage-multiplier", 1.5),
                abilities);
    }

    public Optional<BossData> findById(String id) {
        return Optional.ofNullable(bosses.get(id));
    }

    public Optional<BossData> findByMonsterId(String monsterId) {
        return Optional.ofNullable(byMonsterId.get(monsterId));
    }

    public Map<String, BossData> getAll() {
        return Map.copyOf(bosses);
    }
}
