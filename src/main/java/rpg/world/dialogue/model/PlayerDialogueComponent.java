package rpg.world.dialogue.model;

import rpg.core.player.PlayerDataComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player dialogue flags, persisted so branching/choice history survives a restart.
 */
public final class PlayerDialogueComponent implements PlayerDataComponent {

    private final UUID owner;
    private final Set<String> flags;

    public PlayerDialogueComponent(UUID owner, Set<String> flags) {
        this.owner = owner;
        this.flags = new HashSet<>(flags);
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void setFlag(String flag) {
        flags.add(flag);
    }

    public Set<String> getFlags() {
        return Set.copyOf(flags);
    }
}
