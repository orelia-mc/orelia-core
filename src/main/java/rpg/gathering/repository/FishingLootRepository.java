package rpg.gathering.repository;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.gathering.model.FishingLootEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parses {@code fishing.yml}'s {@code towns} section: which items are catchable, and how
 * often, per town. orelia-core has no first-class town/region concept of its own (that
 * lives in orelia-world), so a town here is just the name of the Bukkit world the player is
 * fishing in - a world with no matching key falls back to the {@code default} table. Adding
 * a town or changing its item list is a config-only change (edit {@code fishing.yml}, run
 * {@code /oladmin reload}) - no code edit needed. Pure data access - never touches Bukkit
 * events or game logic.
 */
public final class FishingLootRepository {

    private static final String DEFAULT_TOWN = "default";

    private final Logger logger;
    private Map<String, List<FishingLootEntry>> lootByTown = Map.of();

    public FishingLootRepository(Logger logger) {
        this.logger = logger;
    }

    public void load(YamlConfiguration config) {
        Map<String, List<FishingLootEntry>> loaded = new HashMap<>();
        ConfigurationSection townsRoot = config.getConfigurationSection("towns");
        if (townsRoot != null) {
            for (String townKey : townsRoot.getKeys(false)) {
                List<FishingLootEntry> entries = new ArrayList<>();
                for (Map<?, ?> raw : townsRoot.getMapList(townKey)) {
                    FishingLootEntry entry = parseEntry(townKey, raw);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                if (!entries.isEmpty()) {
                    loaded.put(townKey.toLowerCase(Locale.ROOT), List.copyOf(entries));
                }
            }
        }
        this.lootByTown = Map.copyOf(loaded);
    }

    private FishingLootEntry parseEntry(String townKey, Map<?, ?> raw) {
        Object itemRaw = raw.get("item");
        Material material = itemRaw == null ? null : parseMaterial(itemRaw.toString());
        if (material == null) {
            logger.warning("fishing.yml towns." + townKey + " has an entry with an unknown/missing item; skipping.");
            return null;
        }
        int weight = Math.max(1, toInt(raw.get("weight"), 1));
        int minAmount = Math.max(1, toInt(raw.get("min-amount"), 1));
        int maxAmount = Math.max(minAmount, toInt(raw.get("max-amount"), minAmount));
        return new FishingLootEntry(material, weight, minAmount, maxAmount);
    }

    private Material parseMaterial(String raw) {
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int toInt(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    /** Catchable-item table for {@code townId} (typically the fishing world's name), falling back to "default". */
    public List<FishingLootEntry> lootFor(String townId) {
        List<FishingLootEntry> exact = lootByTown.get(townId.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return exact;
        }
        return lootByTown.getOrDefault(DEFAULT_TOWN, List.of());
    }
}
