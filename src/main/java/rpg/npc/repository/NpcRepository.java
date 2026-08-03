package rpg.npc.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import rpg.api.ShopEntry;
import rpg.npc.model.NpcData;
import rpg.npc.model.NpcType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link NpcData}, rebuilt from {@code npc.yml}.
 */
public final class NpcRepository {

    private Map<String, NpcData> npcs = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, NpcData> loaded = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("npcs");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection npcSection = section.getConfigurationSection(id);
                if (npcSection == null) {
                    continue;
                }
                loaded.put(id, parse(id, npcSection));
            }
        }
        this.npcs = loaded;
    }

    private NpcData parse(String id, ConfigurationSection section) {
        List<ShopEntry> stock = new ArrayList<>();
        ConfigurationSection stockSection = section.getConfigurationSection("shop-stock");
        if (stockSection != null) {
            for (String stockId : stockSection.getKeys(false)) {
                ConfigurationSection entrySection = stockSection.getConfigurationSection(stockId);
                if (entrySection == null) {
                    continue;
                }
                stock.add(new ShopEntry(
                        entrySection.getString("kind", "WEAPON"),
                        entrySection.getString("id", stockId),
                        entrySection.getDouble("price", 0)));
            }
        }

        return new NpcData(
                id,
                section.getString("name", id),
                NpcType.valueOf(section.getString("type", "GUILD_RECEPTIONIST").trim().toUpperCase()),
                EntityType.valueOf(section.getString("entity-type", "VILLAGER").trim().toUpperCase()),
                section.getString("world", "world"),
                section.getDouble("x", 0),
                section.getDouble("y", 64),
                section.getDouble("z", 0),
                (float) section.getDouble("yaw", 0),
                section.getStringList("dialogue"),
                section.getString("conditional-item-id"),
                section.getStringList("conditional-dialogue"),
                stock,
                section.getStringList("quest-ids"),
                section.getDouble("enhancement-cost-base", 100),
                section.getDouble("enhancement-cost-per-level", 50),
                section.getString("weapon-levelup-item-material", "EMERALD"),
                section.getInt("weapon-levelup-item-amount", 5),
                section.getDouble("weapon-levelup-cost-base", 100),
                section.getDouble("weapon-levelup-cost-per-level", 50));
    }

    public Optional<NpcData> findById(String id) {
        return Optional.ofNullable(npcs.get(id));
    }

    public Map<String, NpcData> getAll() {
        return Map.copyOf(npcs);
    }

    /** Adds a brand-new in-memory NPC definition. Overwrites silently if {@code id} already exists. */
    public void add(String id, NpcData data) {
        npcs.put(id, data);
    }

    /** Replaces an existing in-memory NPC definition (e.g. after moving it). */
    public void replace(String id, NpcData data) {
        npcs.put(id, data);
    }

    /** Removes an NPC definition from memory. Returns {@code false} if {@code id} was not registered. */
    public boolean remove(String id) {
        return npcs.remove(id) != null;
    }
}
