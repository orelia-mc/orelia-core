package rpg.extra.guild.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A persistent player organization (SOW GuildModule). Officers may invite/kick members;
 * only the leader may promote/demote/disband.
 */
public final class Guild {

    private final UUID id;
    private final String name;
    private final String tag;
    private UUID leaderId;
    private final Map<UUID, GuildRole> members;

    public Guild(UUID id, String name, String tag, UUID leaderId, Map<UUID, GuildRole> members) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderId = leaderId;
        this.members = new LinkedHashMap<>(members);
    }

    public static Guild create(String name, String tag, UUID leaderId) {
        Guild guild = new Guild(UUID.randomUUID(), name, tag, leaderId, new LinkedHashMap<>());
        guild.members.put(leaderId, GuildRole.LEADER);
        return guild;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    /** Sets a new leader, demoting the previous leader (if still a member) to {@link GuildRole#OFFICER}. */
    public void setLeaderId(UUID leaderId) {
        UUID previousLeaderId = this.leaderId;
        this.leaderId = leaderId;
        members.put(leaderId, GuildRole.LEADER);
        if (previousLeaderId != null && !previousLeaderId.equals(leaderId) && members.containsKey(previousLeaderId)) {
            members.put(previousLeaderId, GuildRole.OFFICER);
        }
    }

    public Map<UUID, GuildRole> getMembers() {
        return Map.copyOf(members);
    }

    public GuildRole roleOf(UUID playerId) {
        return members.getOrDefault(playerId, null);
    }

    public void addMember(UUID playerId, GuildRole role) {
        members.put(playerId, role);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public void setRole(UUID playerId, GuildRole role) {
        if (members.containsKey(playerId)) {
            members.put(playerId, role);
        }
    }
}
