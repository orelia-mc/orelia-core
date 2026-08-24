package rpg.world.dialogue.model;

import java.util.List;

/**
 * One screen of a conversation: some lines of text, then either a list of
 * {@link DialogueChoice}s (branching) or a single {@code nextNodeId} (linear), or neither
 * (end of conversation). {@code setFlag}, if present, is applied the moment this node is
 * shown (SOW DialogueModule "フラグ更新").
 */
public final class DialogueNode {

    private final String id;
    private final List<String> lines;
    private final List<DialogueChoice> choices;
    private final String nextNodeId;
    private final String setFlag;

    public DialogueNode(String id, List<String> lines, List<DialogueChoice> choices, String nextNodeId, String setFlag) {
        this.id = id;
        this.lines = lines;
        this.choices = choices;
        this.nextNodeId = nextNodeId;
        this.setFlag = setFlag;
    }

    public String getId() {
        return id;
    }

    public List<String> getLines() {
        return lines;
    }

    public List<DialogueChoice> getChoices() {
        return choices;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    public String getSetFlag() {
        return setFlag;
    }
}
