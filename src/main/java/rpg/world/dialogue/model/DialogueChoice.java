package rpg.world.dialogue.model;

/**
 * One player-selectable option on a {@link DialogueNode} (SOW DialogueModule "選択肢").
 */
public final class DialogueChoice {

    private final String text;
    private final String nextNodeId;
    private final String setFlag;

    public DialogueChoice(String text, String nextNodeId, String setFlag) {
        this.text = text;
        this.nextNodeId = nextNodeId;
        this.setFlag = setFlag;
    }

    public String getText() {
        return text;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    public String getSetFlag() {
        return setFlag;
    }
}
