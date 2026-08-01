package rpg.world.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.core.player.PlayerDataManager;
import rpg.dungeon.model.PlayerDungeonComponent;
import rpg.dungeon.repository.DungeonRepository;
import rpg.dungeon.service.DungeonEncounterService;
import rpg.npc.repository.NpcRepository;
import rpg.quest.service.QuestProgressService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class WorldDebugApiImpl implements WorldDebugApi {

    private final ConfigManager configManager;
    private final QuestProgressService questProgressService;
    private final NpcRepository npcRepository;
    private final DungeonRepository dungeonRepository;
    private final DungeonEncounterService dungeonEncounterService;
    private final PlayerDataManager playerDataManager;

    WorldDebugApiImpl(ConfigManager configManager, QuestProgressService questProgressService, NpcRepository npcRepository,
                       DungeonRepository dungeonRepository, DungeonEncounterService dungeonEncounterService,
                       PlayerDataManager playerDataManager) {
        this.configManager = configManager;
        this.questProgressService = questProgressService;
        this.npcRepository = npcRepository;
        this.dungeonRepository = dungeonRepository;
        this.dungeonEncounterService = dungeonEncounterService;
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
    public boolean forceCompleteQuestObjectives(UUID playerId, String questId) {
        return questProgressService.forceCompleteObjectives(playerId, questId);
    }

    @Override
    public boolean forceStartQuest(UUID playerId, String questId) {
        return questProgressService.forceStartQuest(playerId, questId);
    }

    @Override
    public boolean resetQuestCompletion(UUID playerId, String questId) {
        return questProgressService.resetCompletion(playerId, questId);
    }

    @Override
    public List<String> listQuestIds() {
        return questProgressService.listQuestIds().stream().sorted().toList();
    }

    @Override
    public boolean grantTitle(UUID playerId, String title) {
        return questProgressService.grantTitle(playerId, title);
    }

    @Override
    public boolean forceEquipTitle(UUID playerId, String title) {
        return questProgressService.forceEquipTitle(playerId, title);
    }

    @Override
    public boolean unequipTitle(UUID playerId) {
        return questProgressService.unequipTitle(playerId);
    }

    @Override
    public List<String> listNpcIds() {
        return npcRepository.getAll().keySet().stream().sorted().toList();
    }

    @Override
    public List<String> listDungeonIds() {
        return dungeonRepository.getAll().keySet().stream().sorted().toList();
    }

    @Override
    public boolean unlockDungeonForPlayer(UUID playerId, String dungeonId) {
        PlayerDungeonComponent component = playerDataManager.get(playerId)
                .flatMap(d -> d.component(PlayerDungeonComponent.class)).orElse(null);
        if (component == null || dungeonRepository.findById(dungeonId).isEmpty()) {
            return false;
        }
        component.unlock(dungeonId);
        return true;
    }

    @Override
    public Optional<String> forceStartDungeon(UUID playerId, String dungeonId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return Optional.of("PLAYER_OFFLINE");
        }
        return dungeonEncounterService.forceStart(player, dungeonId).map(Enum::name);
    }

    @Override
    public boolean forceEndDungeon(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && dungeonEncounterService.retire(player);
    }

    @Override
    public Optional<String> getActiveDungeonId(UUID playerId) {
        return dungeonEncounterService.getActiveDungeonId(playerId);
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
