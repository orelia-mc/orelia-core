package rpg.extra.guild.service;

import org.bukkit.entity.Player;
import rpg.extra.guild.manager.GuildManager;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRoleDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Guild business rules on top of {@link GuildManager}'s plain data operations. Every
 * member-management/role-management/rename action is leader-only - see {@link Guild}'s own doc
 * comment for why the OFFICER tier being removed simplified the permission model to just
 * "is the leader" (a deliberate behavior change from the old leader-or-officer gate on
 * invite/kick).
 */
public final class GuildService {

    /** Counted by {@code String#length()} (UTF-16 code units) - every character these are meant to bound (kana/kanji included) is one BMP code unit, so this is an accurate character count for the names/tags this is actually validating. */
    public static final int MAX_NAME_LENGTH = 16;
    public static final int MAX_TAG_LENGTH = 5;
    public static final int MAX_ROLE_NAME_LENGTH = 16;
    /** Custom roles only - the reserved leader "role" is never counted against this. */
    public static final int MAX_ROLES_PER_GUILD = 7;

    public enum ActionResult {
        OK, ALREADY_IN_GUILD, NOT_IN_GUILD, INSUFFICIENT_ROLE, TARGET_ALREADY_IN_GUILD,
        NO_PENDING_INVITE, CANNOT_TARGET_SELF, CANNOT_TARGET_LEADER, LEADER_MUST_DISBAND,
        NAME_TAKEN, TAG_TAKEN, NAME_TOO_LONG, TAG_TOO_LONG, TARGET_NOT_MEMBER,
        ROLE_NOT_FOUND, ROLE_NAME_TAKEN, ROLE_NAME_TOO_LONG, TOO_MANY_ROLES, ROLE_IN_USE, LAST_ROLE
    }

    private final GuildManager manager;

    public GuildService(GuildManager manager) {
        this.manager = manager;
    }

    /**
     * {@code name}/{@code tag} uniqueness is case-insensitive via {@link String#equalsIgnoreCase}
     * (full Unicode case-folding, not just ASCII - "Guild"/"guild" collide the same as any
     * Japanese text would under its own casing rules) against every existing guild, checked
     * before length so a duplicate is reported even if it also happens to be too long.
     */
    public ActionResult create(Player leader, String name, String tag) {
        if (manager.getByPlayer(leader.getUniqueId()).isPresent()) {
            return ActionResult.ALREADY_IN_GUILD;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return ActionResult.NAME_TOO_LONG;
        }
        if (tag.length() > MAX_TAG_LENGTH) {
            return ActionResult.TAG_TOO_LONG;
        }
        for (Guild existing : manager.getAll()) {
            if (existing.getName().equalsIgnoreCase(name)) {
                return ActionResult.NAME_TAKEN;
            }
            if (existing.getTag().equalsIgnoreCase(tag)) {
                return ActionResult.TAG_TAKEN;
            }
        }
        manager.create(name, tag, leader.getUniqueId());
        return ActionResult.OK;
    }

    /** Renames the leader's own guild. */
    public ActionResult rename(Player leader, String newName) {
        Guild guild = requireLeaderOf(leader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(leader);
        }
        if (newName.length() > MAX_NAME_LENGTH) {
            return ActionResult.NAME_TOO_LONG;
        }
        for (Guild existing : manager.getAll()) {
            if (!existing.getId().equals(guild.getId()) && existing.getName().equalsIgnoreCase(newName)) {
                return ActionResult.NAME_TAKEN;
            }
        }
        guild.setName(newName);
        manager.persist(guild);
        return ActionResult.OK;
    }

    /** Retags the leader's own guild. */
    public ActionResult retag(Player leader, String newTag) {
        Guild guild = requireLeaderOf(leader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(leader);
        }
        if (newTag.length() > MAX_TAG_LENGTH) {
            return ActionResult.TAG_TOO_LONG;
        }
        for (Guild existing : manager.getAll()) {
            if (!existing.getId().equals(guild.getId()) && existing.getTag().equalsIgnoreCase(newTag)) {
                return ActionResult.TAG_TAKEN;
            }
        }
        guild.setTag(newTag);
        manager.persist(guild);
        return ActionResult.OK;
    }

    public ActionResult invite(Player inviter, Player invitee) {
        if (inviter.getUniqueId().equals(invitee.getUniqueId())) {
            return ActionResult.CANNOT_TARGET_SELF;
        }
        Guild guild = manager.getByPlayer(inviter.getUniqueId()).orElse(null);
        if (guild == null) {
            return ActionResult.NOT_IN_GUILD;
        }
        if (!guild.getLeaderId().equals(inviter.getUniqueId())) {
            return ActionResult.INSUFFICIENT_ROLE;
        }
        if (manager.getByPlayer(invitee.getUniqueId()).isPresent()) {
            return ActionResult.TARGET_ALREADY_IN_GUILD;
        }
        manager.invite(guild.getId(), invitee.getUniqueId());
        return ActionResult.OK;
    }

    /** Accepts the oldest (first-received) pending invite. */
    public ActionResult accept(Player invitee) {
        return accept(invitee, manager.peekInvite(invitee.getUniqueId()).map(Guild::getId).orElse(null));
    }

    /** Accepts a specific guild's invite (not necessarily the oldest) - {@code guildId} null-safe. */
    public ActionResult accept(Player invitee, UUID guildId) {
        if (guildId == null) {
            return ActionResult.NO_PENDING_INVITE;
        }
        Optional<Guild> guild = manager.consumeInvite(invitee.getUniqueId(), guildId);
        if (guild.isEmpty()) {
            return ActionResult.NO_PENDING_INVITE;
        }
        manager.addMember(guild.get(), invitee.getUniqueId(), guild.get().defaultMemberRoleId());
        return ActionResult.OK;
    }

    /** Declines the oldest (first-received) pending invite. */
    public ActionResult decline(Player invitee) {
        return decline(invitee, manager.peekInvite(invitee.getUniqueId()).map(Guild::getId).orElse(null));
    }

    /** Declines a specific guild's invite (not necessarily the oldest). */
    public ActionResult decline(Player invitee, UUID guildId) {
        if (guildId == null) {
            return ActionResult.NO_PENDING_INVITE;
        }
        return manager.consumeInvite(invitee.getUniqueId(), guildId).isPresent()
                ? ActionResult.OK : ActionResult.NO_PENDING_INVITE;
    }

    public ActionResult leave(Player player) {
        Guild guild = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (guild == null) {
            return ActionResult.NOT_IN_GUILD;
        }
        if (guild.getLeaderId().equals(player.getUniqueId())) {
            return ActionResult.LEADER_MUST_DISBAND;
        }
        manager.removeMember(guild, player.getUniqueId());
        return ActionResult.OK;
    }

    /** Hands leadership to {@code newLeaderId} (must already be a member), demoting the old leader to the guild's default role. */
    public ActionResult transferLeadership(Player currentLeader, UUID newLeaderId) {
        Guild guild = requireLeaderOf(currentLeader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(currentLeader);
        }
        if (!guild.getMembers().containsKey(newLeaderId)) {
            return ActionResult.TARGET_NOT_MEMBER;
        }
        guild.setLeaderId(newLeaderId);
        manager.persist(guild);
        return ActionResult.OK;
    }

    public ActionResult kick(Player leader, UUID targetId) {
        Guild guild = requireLeaderOf(leader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(leader);
        }
        if (targetId.equals(guild.getLeaderId())) {
            return ActionResult.CANNOT_TARGET_LEADER;
        }
        manager.removeMember(guild, targetId);
        return ActionResult.OK;
    }

    /** Assigns one of the guild's own {@link GuildRoleDefinition}s to a member - replaces the old fixed promote/demote. */
    public ActionResult assignRole(Player leader, UUID targetId, String roleId) {
        Guild guild = requireLeaderOf(leader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(leader);
        }
        if (targetId.equals(guild.getLeaderId())) {
            return ActionResult.CANNOT_TARGET_LEADER;
        }
        if (!guild.getMembers().containsKey(targetId)) {
            return ActionResult.TARGET_NOT_MEMBER;
        }
        if (guild.roleDefinition(roleId).isEmpty()) {
            return ActionResult.ROLE_NOT_FOUND;
        }
        guild.setRole(targetId, roleId);
        manager.persist(guild);
        return ActionResult.OK;
    }

    /** Adds a new custom role, capped at {@link #MAX_ROLES_PER_GUILD}. */
    public ActionResult addRole(Player leader, String name) {
        Guild guild = requireLeaderOf(leader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(leader);
        }
        if (name.length() > MAX_ROLE_NAME_LENGTH) {
            return ActionResult.ROLE_NAME_TOO_LONG;
        }
        if (guild.getRoles().size() >= MAX_ROLES_PER_GUILD) {
            return ActionResult.TOO_MANY_ROLES;
        }
        if (guild.hasRoleNamed(name)) {
            return ActionResult.ROLE_NAME_TAKEN;
        }
        int sortOrder = guild.getRoles().size();
        guild.addRole(new GuildRoleDefinition(UUID.randomUUID().toString().substring(0, 8), name, sortOrder));
        manager.persist(guild);
        return ActionResult.OK;
    }

    /** Renames an existing custom role, resolved by its current name (guild-scoped, not global). */
    public ActionResult renameRole(Player leader, String currentRoleName, String newName) {
        Guild guild = requireLeaderOf(leader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(leader);
        }
        GuildRoleDefinition role = findRoleByName(guild, currentRoleName).orElse(null);
        if (role == null) {
            return ActionResult.ROLE_NOT_FOUND;
        }
        if (newName.length() > MAX_ROLE_NAME_LENGTH) {
            return ActionResult.ROLE_NAME_TOO_LONG;
        }
        if (!role.name().equalsIgnoreCase(newName) && guild.hasRoleNamed(newName)) {
            return ActionResult.ROLE_NAME_TAKEN;
        }
        guild.renameRole(role.id(), newName);
        manager.persist(guild);
        return ActionResult.OK;
    }

    /** Deletes a custom role, resolved by name. Refuses if it's the guild's last remaining role, or if any member currently holds it. */
    public ActionResult deleteRole(Player leader, String roleName) {
        Guild guild = requireLeaderOf(leader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(leader);
        }
        GuildRoleDefinition role = findRoleByName(guild, roleName).orElse(null);
        if (role == null) {
            return ActionResult.ROLE_NOT_FOUND;
        }
        if (guild.getRoles().size() <= 1) {
            return ActionResult.LAST_ROLE;
        }
        boolean inUse = guild.getMembers().values().stream().anyMatch(role.id()::equals);
        if (inUse) {
            return ActionResult.ROLE_IN_USE;
        }
        guild.removeRole(role.id());
        manager.persist(guild);
        return ActionResult.OK;
    }

    private Optional<GuildRoleDefinition> findRoleByName(Guild guild, String name) {
        return guild.getRoles().stream().filter(role -> role.name().equalsIgnoreCase(name)).findFirst();
    }

    /** {@code player}'s own guild, only if they're its leader - the shared gate every leader-only action above uses. */
    private Optional<Guild> requireLeaderOf(Player player) {
        return manager.getByPlayer(player.getUniqueId()).filter(guild -> guild.getLeaderId().equals(player.getUniqueId()));
    }

    /** NOT_IN_GUILD if the player has no guild at all, otherwise INSUFFICIENT_ROLE (they're a member but not the leader). */
    private ActionResult leaderGuildFailure(Player player) {
        return manager.getByPlayer(player.getUniqueId()).isEmpty() ? ActionResult.NOT_IN_GUILD : ActionResult.INSUFFICIENT_ROLE;
    }

    public ActionResult disband(Player leader) {
        Guild guild = requireLeaderOf(leader).orElse(null);
        if (guild == null) {
            return leaderGuildFailure(leader);
        }
        manager.disband(guild);
        return ActionResult.OK;
    }

    public Optional<Guild> getGuild(UUID playerId) {
        return manager.getByPlayer(playerId);
    }

    /** The oldest guild {@code playerId} has a pending invite to, if any - for the GUI's accept/decline prompt. */
    public Optional<Guild> peekPendingInvite(UUID playerId) {
        return manager.peekInvite(playerId);
    }

    /** Every guild currently inviting {@code playerId}, oldest first - for the GUI's ordered pending-invites list. */
    public List<Guild> peekAllPendingInvites(UUID playerId) {
        return manager.peekAllInvites(playerId);
    }

    /** Looks up a guild by its own id rather than a member's - for the GUI browser, which drills from a list into one guild's detail. */
    public Optional<Guild> getGuildById(UUID guildId) {
        return manager.getById(guildId);
    }

    public Collection<Guild> getAllGuilds() {
        return manager.getAll();
    }
}
