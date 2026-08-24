package rpg.world.api;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.core.player.PlayerDataManager;
import rpg.dungeon.gui.DungeonGuiScreen;
import rpg.dungeon.model.PlayerDungeonComponent;
import rpg.dungeon.repository.DungeonRepository;
import rpg.dungeon.service.DungeonEncounterService;
import rpg.gui.framework.GuiManager;
import rpg.npc.repository.NpcRepository;
import rpg.quest.gui.QuestGuiScreen;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.model.PlayerQuestProgress;
import rpg.quest.model.QuestData;
import rpg.quest.model.QuestObjective;
import rpg.quest.model.QuestReward;
import rpg.quest.repository.QuestRepository;
import rpg.quest.service.QuestProgressService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class WorldDebugApiImpl implements WorldDebugApi {

    private final ConfigManager configManager;
    private final QuestProgressService questProgressService;
    private final QuestGuiScreen questGuiScreen;
    private final QuestRepository questRepository;
    private final NpcRepository npcRepository;
    private final DungeonRepository dungeonRepository;
    private final DungeonEncounterService dungeonEncounterService;
    private final DungeonGuiScreen dungeonGuiScreen;
    private final PlayerDataManager playerDataManager;
    private final GuiManager guiManager = new GuiManager();

    WorldDebugApiImpl(ConfigManager configManager, QuestProgressService questProgressService, QuestGuiScreen questGuiScreen,
                       QuestRepository questRepository, NpcRepository npcRepository, DungeonRepository dungeonRepository,
                       DungeonEncounterService dungeonEncounterService, DungeonGuiScreen dungeonGuiScreen,
                       PlayerDataManager playerDataManager) {
        this.configManager = configManager;
        this.questProgressService = questProgressService;
        this.questGuiScreen = questGuiScreen;
        this.questRepository = questRepository;
        this.npcRepository = npcRepository;
        this.dungeonRepository = dungeonRepository;
        this.dungeonEncounterService = dungeonEncounterService;
        this.dungeonGuiScreen = dungeonGuiScreen;
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
    public List<QuestDefinition> listQuestDefinitions() {
        return questRepository.getAll().values().stream().map(this::toDefinition).toList();
    }

    @Override
    public Optional<QuestDefinition> getQuestDefinition(String questId) {
        return questRepository.findById(questId).map(this::toDefinition);
    }

    private QuestDefinition toDefinition(QuestData quest) {
        List<QuestObjectiveInfo> objectives = quest.getObjectives().stream()
                .map(this::toObjectiveInfo).toList();
        return new QuestDefinition(quest.getId(), quest.getName(), quest.getType().name(), quest.getDescription(),
                quest.getRequiredLevel(), quest.isRepeatable(), quest.isPartyOnly(), quest.getPrerequisiteQuestIds(),
                quest.getCooldownHours(), objectives, toRewardInfo(quest.getReward()));
    }

    private QuestObjectiveInfo toObjectiveInfo(QuestObjective objective) {
        return new QuestObjectiveInfo(objective.getType().name(), objective.getTargetId(), objective.getRequiredAmount());
    }

    private QuestRewardInfo toRewardInfo(QuestReward reward) {
        return new QuestRewardInfo(reward.getExp(), reward.getMoney(), reward.getWeaponId(), reward.getAccessoryId(),
                reward.getSkillPoints(), reward.getTitle(), reward.getVanillaMaterial(), reward.getVanillaAmount());
    }

    @Override
    public Optional<QuestProgressDetail> getQuestProgressDetail(UUID playerId, String questId) {
        PlayerQuestComponent component = playerDataManager.get(playerId)
                .flatMap(d -> d.component(PlayerQuestComponent.class)).orElse(null);
        if (component == null) {
            return Optional.empty();
        }
        PlayerQuestProgress progress = component.getActiveQuests().get(questId);
        if (progress == null) {
            return Optional.empty();
        }
        return questRepository.findById(questId).map(quest -> toProgressDetail(quest, progress));
    }

    @Override
    public List<QuestProgressDetail> listActiveQuestProgress(UUID playerId) {
        PlayerQuestComponent component = playerDataManager.get(playerId)
                .flatMap(d -> d.component(PlayerQuestComponent.class)).orElse(null);
        if (component == null) {
            return List.of();
        }
        return component.getActiveQuests().entrySet().stream()
                .map(entry -> questRepository.findById(entry.getKey()).map(quest -> toProgressDetail(quest, entry.getValue())))
                .filter(Optional::isPresent).map(Optional::get).toList();
    }

    private QuestProgressDetail toProgressDetail(QuestData quest, PlayerQuestProgress progress) {
        List<QuestObjectiveProgressInfo> objectives = new ArrayList<>();
        List<QuestObjective> defs = quest.getObjectives();
        for (int i = 0; i < defs.size(); i++) {
            QuestObjective objective = defs.get(i);
            objectives.add(new QuestObjectiveProgressInfo(objective.getType().name(), objective.getTargetId(),
                    Math.min(progress.getProgress(i), objective.getRequiredAmount()), objective.getRequiredAmount()));
        }
        return new QuestProgressDetail(quest.getId(), quest.getName(), progress.getState().name(), objectives);
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

    @Override
    public void openDungeon(Player player) {
        guiManager.open(player, dungeonGuiScreen.build(player));
    }

    @Override
    public void openQuest(Player player) {
        guiManager.open(player, questGuiScreen.build(player));
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
