package rpg.api;

import org.bukkit.configuration.ConfigurationSection;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.core.player.PlayerData;
import rpg.core.player.PlayerDataManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class DebugApiImpl implements DebugApi {

    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;

    DebugApiImpl(ConfigManager configManager, PlayerDataManager playerDataManager) {
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public Set<String> listConfigFiles() {
        return configManager.getRegisteredFileNames();
    }

    @Override
    public Optional<String> getConfigValue(String fileName, String path) {
        ConfigFile file = tryGet(fileName);
        if (file == null || !file.get().contains(path)) {
            return Optional.empty();
        }
        return Optional.ofNullable(file.get().get(path)).map(String::valueOf);
    }

    @Override
    public boolean setConfigValue(String fileName, String path, String rawValue) {
        ConfigFile file = tryGet(fileName);
        if (file == null) {
            return false;
        }
        file.get().set(path, parseValue(rawValue));
        file.save();
        return true;
    }

    @Override
    public void saveConfig(String fileName) {
        ConfigFile file = tryGet(fileName);
        if (file != null) {
            file.save();
        }
    }

    @Override
    public List<String> describeConfigKeys(String fileName) {
        ConfigFile file = tryGet(fileName);
        if (file == null) {
            return List.of();
        }
        return file.get().getKeys(true).stream().sorted().toList();
    }

    @Override
    public List<ConfigTreeEntry> listConfigTree(String fileName) {
        ConfigFile file = tryGet(fileName);
        if (file == null) {
            return List.of();
        }
        List<ConfigTreeEntry> entries = new ArrayList<>();
        collectTree(file.get(), "", 0, entries);
        return entries;
    }

    private void collectTree(ConfigurationSection section, String pathPrefix, int depth, List<ConfigTreeEntry> out) {
        for (String key : section.getKeys(false)) {
            String path = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            if (section.isConfigurationSection(key)) {
                out.add(new ConfigTreeEntry(path, depth, key, null, false));
                collectTree(section.getConfigurationSection(key), path, depth + 1, out);
            } else {
                out.add(new ConfigTreeEntry(path, depth, key, String.valueOf(section.get(key)), true));
            }
        }
    }

    @Override
    public boolean isDebugMode(UUID playerId) {
        return playerDataManager.get(playerId).map(PlayerData::isDebugMode).orElse(false);
    }

    @Override
    public boolean setDebugMode(UUID playerId, boolean enabled) {
        return playerDataManager.get(playerId).map(data -> {
            data.setDebugMode(enabled);
            return true;
        }).orElse(false);
    }

    private ConfigFile tryGet(String fileName) {
        try {
            return configManager.get(fileName);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private Object parseValue(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
            return Boolean.parseBoolean(rawValue);
        }
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException ignored) {
        }
        return rawValue;
    }
}
