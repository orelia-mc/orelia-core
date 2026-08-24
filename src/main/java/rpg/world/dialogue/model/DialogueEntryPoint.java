package rpg.world.dialogue.model;

/**
 * One candidate starting node for a {@link DialogueTree}. Entry points are evaluated in
 * config order; the first whose {@code requiredFlag} the player has (or which has none)
 * wins - this is how a tree changes its opening line based on prior flags (SOW
 * DialogueModule "条件分岐").
 */
public final class DialogueEntryPoint {

    private final String requiredFlag;
    private final String startNodeId;

    public DialogueEntryPoint(String requiredFlag, String startNodeId) {
        this.requiredFlag = requiredFlag;
        this.startNodeId = startNodeId;
    }

    public String getRequiredFlag() {
        return requiredFlag;
    }

    public String getStartNodeId() {
        return startNodeId;
    }
}
