package rpg.gathering.repository;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.gathering.model.CropTemplate;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.model.GatherBlockTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses {@code gathering.yml}'s {@code gather-settings} (mining/woodcutting block
 * templates) and {@code farm-settings} (crop templates) sections into in-memory
 * templates. Pure data access - never touches Bukkit events or game logic.
 */
public final class GatheringDefinitionRepository {

    private final Logger logger;
    private Map<Material, GatherBlockTemplate> gatherBlocks = Map.of();
    private Map<Material, CropTemplate> crops = Map.of();
    private Map<Material, Material> seedToCrop = Map.of();

    public GatheringDefinitionRepository(Logger logger) {
        this.logger = logger;
    }

    public void load(YamlConfiguration config) {
        this.gatherBlocks = Map.copyOf(loadGatherBlocks(config));
        Map<Material, CropTemplate> cropLoaded = new HashMap<>();
        Map<Material, Material> seedLoaded = new HashMap<>();
        loadCrops(config, cropLoaded, seedLoaded);
        this.crops = Map.copyOf(cropLoaded);
        this.seedToCrop = Map.copyOf(seedLoaded);
    }

    private Map<Material, GatherBlockTemplate> loadGatherBlocks(YamlConfiguration config) {
        Map<Material, GatherBlockTemplate> loaded = new HashMap<>();
        ConfigurationSection gatherRoot = config.getConfigurationSection("gather-settings");
        if (gatherRoot == null) {
            return loaded;
        }
        for (String actionKey : gatherRoot.getKeys(false)) {
            GatherActionType actionType = parseActionType(actionKey);
            ConfigurationSection actionSection = gatherRoot.getConfigurationSection(actionKey);
            if (actionType == null || actionSection == null) {
                continue;
            }
            for (String blockKey : actionSection.getKeys(false)) {
                Material blockType = parseMaterial(blockKey);
                ConfigurationSection section = actionSection.getConfigurationSection(blockKey);
                if (blockType == null || section == null) {
                    continue;
                }
                Material replaceBlock = parseMaterial(section.getString("replace-block", "AIR"));
                loaded.put(blockType, new GatherBlockTemplate(
                        blockType,
                        actionType,
                        Math.max(1, section.getInt("cooldown-seconds", 60)),
                        replaceBlock == null ? Material.AIR : replaceBlock,
                        section.getInt("xp-gain", 0),
                        section.getInt("min-level", 0)));
            }
        }
        return loaded;
    }

    private void loadCrops(YamlConfiguration config, Map<Material, CropTemplate> cropLoaded, Map<Material, Material> seedLoaded) {
        ConfigurationSection farmRoot = config.getConfigurationSection("farm-settings");
        if (farmRoot == null) {
            return;
        }
        for (String cropKey : farmRoot.getKeys(false)) {
            Material cropType = parseMaterial(cropKey);
            ConfigurationSection section = farmRoot.getConfigurationSection(cropKey);
            if (cropType == null || section == null) {
                continue;
            }
            Material seedItem = parseMaterial(section.getString("seed-item", null));
            if (seedItem == null) {
                logger.warning("farm-settings." + cropKey + " is missing a valid seed-item; skipping.");
                continue;
            }
            cropLoaded.put(cropType, new CropTemplate(cropType, seedItem, section.getInt("xp-gain", 0)));
            seedLoaded.put(seedItem, cropType);
        }
    }

    private GatherActionType parseActionType(String raw) {
        try {
            return GatherActionType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Unknown gather action type in gathering.yml: " + raw);
            return null;
        }
    }

    private Material parseMaterial(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Unknown material in gathering.yml: " + raw);
            return null;
        }
    }

    public Map<Material, GatherBlockTemplate> getGatherBlocks() {
        return gatherBlocks;
    }

    public Map<Material, CropTemplate> getCrops() {
        return crops;
    }

    public Map<Material, Material> getSeedToCrop() {
        return seedToCrop;
    }
}
