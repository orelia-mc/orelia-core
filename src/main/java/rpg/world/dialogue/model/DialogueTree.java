package rpg.world.dialogue.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A full conversation graph loaded from one entry in {@code dialogues.yml}.
 */
public final class DialogueTree {

    private final String id;
    private final List<DialogueEntryPoint> entryPoints;
    private final Map<String, DialogueNode> nodes;

    public DialogueTree(String id, List<DialogueEntryPoint> entryPoints, Map<String, DialogueNode> nodes) {
        this.id = id;
        this.entryPoints = entryPoints;
        this.nodes = nodes;
    }

    public String getId() {
        return id;
    }

    public List<DialogueEntryPoint> getEntryPoints() {
        return entryPoints;
    }

    public Optional<DialogueNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }
}
