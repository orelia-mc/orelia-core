package rpg.world.dialogue.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import rpg.core.player.PlayerDataManager;
import rpg.util.ColorUtil;
import rpg.world.dialogue.model.DialogueChoice;
import rpg.world.dialogue.model.DialogueEntryPoint;
import rpg.world.dialogue.model.DialogueNode;
import rpg.world.dialogue.model.DialogueTree;
import rpg.world.dialogue.model.PlayerDialogueComponent;
import rpg.world.dialogue.repository.DialogueRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives one player through a {@link DialogueTree}: picks the right entry point for their
 * current flags, prints each node's lines, and renders choices as clickable chat text
 * (SOW DialogueModule "選択肢") that runs {@code /ol dialoguechoice <n>}.
 */
public final class DialogueSessionService {

    private record ActiveSession(String treeId, String nodeId) {
    }

    private final DialogueRepository repository;
    private final PlayerDataManager playerDataManager;
    private final Map<UUID, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    public DialogueSessionService(DialogueRepository repository, PlayerDataManager playerDataManager) {
        this.repository = repository;
        this.playerDataManager = playerDataManager;
    }

    public boolean start(Player player, String treeId) {
        DialogueTree tree = repository.findById(treeId).orElse(null);
        if (tree == null) {
            return false;
        }
        PlayerDialogueComponent component = component(player);
        String startNode = tree.getEntryPoints().stream()
                .filter(entry -> entry.getRequiredFlag() == null || (component != null && component.hasFlag(entry.getRequiredFlag())))
                .map(DialogueEntryPoint::getStartNodeId)
                .findFirst()
                .orElse(null);
        if (startNode == null) {
            return false;
        }
        showNode(player, tree, startNode);
        return true;
    }

    public void choose(Player player, int choiceIndex) {
        ActiveSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        DialogueTree tree = repository.findById(session.treeId()).orElse(null);
        DialogueNode node = tree == null ? null : tree.getNode(session.nodeId()).orElse(null);
        if (node == null || choiceIndex < 0 || choiceIndex >= node.getChoices().size()) {
            return;
        }
        DialogueChoice choice = node.getChoices().get(choiceIndex);
        applyFlag(player, choice.getSetFlag());
        if (choice.getNextNodeId() != null) {
            showNode(player, tree, choice.getNextNodeId());
        } else {
            activeSessions.remove(player.getUniqueId());
        }
    }

    private void showNode(Player player, DialogueTree tree, String nodeId) {
        DialogueNode node = tree.getNode(nodeId).orElse(null);
        if (node == null) {
            activeSessions.remove(player.getUniqueId());
            return;
        }
        applyFlag(player, node.getSetFlag());

        for (String line : node.getLines()) {
            player.sendMessage(ColorUtil.colorize(line));
        }

        List<DialogueChoice> choices = node.getChoices();
        if (!choices.isEmpty()) {
            activeSessions.put(player.getUniqueId(), new ActiveSession(tree.getId(), nodeId));
            for (int i = 0; i < choices.size(); i++) {
                Component line = Component.text((i + 1) + ". " + choices.get(i).getText(), NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/ol dialoguechoice " + i));
                player.sendMessage(line);
            }
        } else if (node.getNextNodeId() != null) {
            activeSessions.put(player.getUniqueId(), new ActiveSession(tree.getId(), nodeId));
            showNode(player, tree, node.getNextNodeId());
        } else {
            activeSessions.remove(player.getUniqueId());
        }
    }

    private void applyFlag(Player player, String flag) {
        if (flag == null || flag.isBlank()) {
            return;
        }
        PlayerDialogueComponent component = component(player);
        if (component != null) {
            component.setFlag(flag);
        }
    }

    private PlayerDialogueComponent component(Player player) {
        return playerDataManager.get(player.getUniqueId())
                .flatMap(data -> data.component(PlayerDialogueComponent.class))
                .orElse(null);
    }
}
