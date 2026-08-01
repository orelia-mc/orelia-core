package rpg.extra.party.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A runtime-only (not persisted across restarts) group of players (SOW PartyModule).
 */
public final class Party {

    private final UUID id = UUID.randomUUID();
    private UUID leaderId;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final int maxSize;

    public Party(UUID leaderId, int maxSize) {
        this.leaderId = leaderId;
        this.maxSize = maxSize;
        this.members.add(leaderId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public Set<UUID> getMembers() {
        return Set.copyOf(members);
    }

    public boolean isFull() {
        return members.size() >= maxSize;
    }

    public boolean addMember(UUID playerId) {
        return !isFull() && members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}
