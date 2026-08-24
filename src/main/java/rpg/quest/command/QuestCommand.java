package rpg.quest.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.gui.framework.GuiManager;
import rpg.quest.gui.QuestGuiScreen;
import rpg.quest.gui.QuestObjectiveBarRenderer;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.model.PlayerQuestProgress;
import rpg.quest.model.QuestData;
import rpg.quest.model.QuestObjective;
import rpg.quest.repository.QuestRepository;
import rpg.quest.service.QuestProgressService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ol quest list} - shows the sender's active quests, their state, and a progress
 * bar per objective. {@code /ol quest abandon <id>} - drops an in-progress quest without
 * rewards.
 */
public final class QuestCommand implements CommandExecutor, TabCompleter {

    private static final int BAR_LENGTH = 10;

    private final PlayerDataManager playerDataManager;
    private final QuestRepository questRepository;
    private final QuestProgressService progressService;
    private final MessageManager messages;
    private final QuestGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final QuestObjectiveBarRenderer barRenderer = new QuestObjectiveBarRenderer();

    public QuestCommand(PlayerDataManager playerDataManager, QuestRepository questRepository,
                         QuestProgressService progressService, MessageManager messages,
                         QuestGuiScreen guiScreen, GuiManager guiManager) {
        this.playerDataManager = playerDataManager;
        this.questRepository = questRepository;
        this.progressService = progressService;
        this.messages = messages;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("gui")) {
            guiManager.open(player, guiScreen.build(player));
            return true;
        }

        PlayerQuestComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerQuestComponent.class))
                .orElse(null);
        if (component == null) {
            messages.send(sender, "quest.data-not-loaded");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("abandon")) {
            String questId = args[1];
            if (progressService.abandon(player.getUniqueId(), questId)) {
                messages.send(sender, "quest.abandoned", "quest", questId);
            } else {
                messages.send(sender, "quest.not-active");
            }
            return true;
        }

        if (component.getActiveQuests().isEmpty()) {
            messages.send(sender, "quest.no-active");
            return true;
        }
        messages.send(sender, "quest.active-header");
        for (var entry : component.getActiveQuests().entrySet()) {
            PlayerQuestProgress progress = entry.getValue();
            messages.sendRaw(sender, "quest.active-entry", "quest", entry.getKey(), "state", progress.getState());
            QuestData quest = questRepository.findById(entry.getKey()).orElse(null);
            if (quest == null) {
                continue;
            }
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                int current = Math.min(progress.getProgress(i), objective.getRequiredAmount());
                String bar = barRenderer.render(current, objective.getRequiredAmount(), BAR_LENGTH, "&%a", "&%8");
                messages.sendRaw(sender, "quest.objective-line",
                        "type", objective.getType(), "target", objective.getTargetId(),
                        "bar", bar, "current", current, "max", objective.getRequiredAmount());
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return matching(List.of("list", "gui", "abandon"), args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("abandon") && sender instanceof Player player) {
            PlayerQuestComponent component = playerDataManager.get(player.getUniqueId())
                    .flatMap(d -> d.component(PlayerQuestComponent.class))
                    .orElse(null);
            if (component != null) {
                return matching(component.getActiveQuests().keySet(), args[1]);
            }
        }
        return List.of();
    }

    private List<String> matching(Iterable<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
