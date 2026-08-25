package rpg.extra.guild.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A persistent player organization (SOW GuildModule). Only the leader may invite/kick/manage
 * roles/disband/rename - the previous LEADER/OFFICER/MEMBER fixed ladder is gone; a non-leader
 * member instead holds one of the guild's own freely-named {@link GuildRoleDefinition}s (a pure
 * display label, no permissions of its own - see that class's own doc comment for why the
 * permission model simplified to just "is the leader" once the middle tier stopped being a
 * fixed, code-known concept).
 */
public final class Guild {

    /** Reserved role id for the guild leader - never a row in {@link #roles}, never assignable via {@code GuildService#assignRole}. */
    public static final String LEADER_ROLE_ID = "LEADER";

    private final UUID id;
    private String name;
    private String tag;
    private UUID leaderId;
    /** playerId -> role id ({@link #LEADER_ROLE_ID} or one of {@link #roles}' ids). */
    private final Map<UUID, String> members;
    /** This guild's own custom roles, sortOrder ascending. Never empty while the guild exists - {@code GuildService#deleteRole} refuses to remove the last one. */
    private final List<GuildRoleDefinition> roles;

    public Guild(UUID id, String name, String tag, UUID leaderId, Map<UUID, String> members, List<GuildRoleDefinition> roles) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderId = leaderId;
        this.members = new LinkedHashMap<>(members);
        this.roles = new ArrayList<>(roles);
        this.roles.sort(Comparator.comparingInt(GuildRoleDefinition::sortOrder));
    }

    /** Seeds two default roles (幹部/メンバー) so a freshly created guild looks/behaves like the old fixed ladder until the leader customizes it. */
    public static Guild create(String name, String tag, UUID leaderId) {
        List<GuildRoleDefinition> defaultRoles = new ArrayList<>();
        defaultRoles.add(new GuildRoleDefinition(UUID.randomUUID().toString().substring(0, 8), "幹部", 0));
        defaultRoles.add(new GuildRoleDefinition(UUID.randomUUID().toString().substring(0, 8), "メンバー", 1));
        Guild guild = new Guild(UUID.randomUUID(), name, tag, leaderId, new LinkedHashMap<>(), defaultRoles);
        guild.members.put(leaderId, LEADER_ROLE_ID);
        return guild;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    /** Sets a new leader, demoting the previous leader (if still a member) to {@link #defaultMemberRoleId()}. */
    public void setLeaderId(UUID leaderId) {
        UUID previousLeaderId = this.leaderId;
        this.leaderId = leaderId;
        members.put(leaderId, LEADER_ROLE_ID);
        if (previousLeaderId != null && !previousLeaderId.equals(leaderId) && members.containsKey(previousLeaderId)) {
            members.put(previousLeaderId, defaultMemberRoleId());
        }
    }

    public Map<UUID, String> getMembers() {
        return Map.copyOf(members);
    }

    /** {@link #LEADER_ROLE_ID}, a {@link #roles} id, or {@code null} if {@code playerId} isn't a member. */
    public String roleOf(UUID playerId) {
        return members.get(playerId);
    }

    /**
     * Display label for {@code roleId} - {@link #LEADER_ROLE_ID} resolves to "リーダー" without a
     * {@link #roles} lookup (it's never stored there), an unresolvable id (a role deleted out from
     * under a stale reference) falls back to the raw id rather than throwing.
     */
    public String roleDisplayName(String roleId) {
        if (LEADER_ROLE_ID.equals(roleId)) {
            return "リーダー";
        }
        return roleDefinition(roleId).map(GuildRoleDefinition::name).orElse(roleId);
    }

    /** Adds a member under the given role id - used when a pending invite is accepted. */
    public void addMember(UUID playerId, String roleId) {
        members.put(playerId, roleId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    /** No-ops if {@code playerId} isn't a member - same fail-quiet shape as the original enum-based version. */
    public void setRole(UUID playerId, String roleId) {
        if (members.containsKey(playerId)) {
            members.put(playerId, roleId);
        }
    }

    public List<GuildRoleDefinition> getRoles() {
        return List.copyOf(roles);
    }

    public Optional<GuildRoleDefinition> roleDefinition(String roleId) {
        return roles.stream().filter(role -> role.id().equals(roleId)).findFirst();
    }

    public boolean hasRoleNamed(String name) {
        return roles.stream().anyMatch(role -> role.name().equalsIgnoreCase(name));
    }

    public void addRole(GuildRoleDefinition role) {
        roles.add(role);
    }

    public void renameRole(String roleId, String newName) {
        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i).id().equals(roleId)) {
                roles.set(i, new GuildRoleDefinition(roleId, newName, roles.get(i).sortOrder()));
                return;
            }
        }
    }

    public void removeRole(String roleId) {
        roles.removeIf(role -> role.id().equals(roleId));
    }

    /**
     * The role a newly-accepted member, or a leader stepping down via {@link #setLeaderId}, ends
     * up with: the guild's last (highest-sortOrder) role - the seeded default is "メンバー" at
     * sortOrder 1, matching the old MEMBER tier exactly; a leader that deletes every custom role
     * but one always keeps at least this one (see this class's own doc comment on {@link #roles}
     * never being empty).
     */
    public String defaultMemberRoleId() {
        return roles.isEmpty() ? LEADER_ROLE_ID : roles.get(roles.size() - 1).id();
    }
}
