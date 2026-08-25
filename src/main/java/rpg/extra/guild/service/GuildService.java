package rpg.extra.guild.service;

import org.bukkit.entity.Player;
import rpg.extra.guild.manager.GuildManager;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRole;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Guild business rules on top of {@link GuildManager}'s plain data operations.
 */
public final class GuildService {

    /** Counted by {@code String#length()} (UTF-16 code units) - every character these are meant to bound (kana/kanji included) is one BMP code unit, so this is an accurate character count for the names/tags this is actually validating. */
    public static final int MAX_NAME_LENGTH = 16;
    public static final int MAX_TAG_LENGTH = 5;

    public enum ActionResult {
        OK, ALREADY_IN_GUILD, NOT_IN_GUILD, INSUFFICIENT_ROLE, TARGET_ALREADY_IN_GUILD,
        NO_PENDING_INVITE, CANNOT_TARGET_SELF, CANNOT_TARGET_LEADER, LEADER_MUST_DISBAND,
        NAME_TAKEN, TAG_TAKEN, NAME_TOO_LONG, TAG_TOO_LONG, TARGET_NOT_MEMBER
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

    public ActionResult invite(Player inviter, Player invitee) {
        if (inviter.getUniqueId().equals(invitee.getUniqueId())) {
            return ActionResult.CANNOT_TARGET_SELF;
        }
        Guild guild = manager.getByPlayer(inviter.getUniqueId()).orElse(null);
        if (guild == null) {
            return ActionResult.NOT_IN_GUILD;
        }
        if (!isOfficerOrAbove(guild, inviter.getUniqueId())) {
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
        manager.addMember(guild.get(), invitee.getUniqueId(), GuildRole.MEMBER);
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

    /** Hands leadership to {@code newLeaderId} (must already be a member), demoting the old leader to officer. */
    public ActionResult transferLeadership(Player currentLeader, UUID newLeaderId) {
        Guild guild = manager.getByPlayer(currentLeader.getUniqueId()).orElse(null);
        if (guild == null) {
            return ActionResult.NOT_IN_GUILD;
        }
        if (!guild.getLeaderId().equals(currentLeader.getUniqueId())) {
            return ActionResult.INSUFFICIENT_ROLE;
        }
        if (!guild.getMembers().containsKey(newLeaderId)) {
            return ActionResult.TARGET_NOT_MEMBER;
        }
        guild.setLeaderId(newLeaderId);
        manager.persist(guild);
        return ActionResult.OK;
    }

    public ActionResult kick(Player actor, UUID targetId) {
        Guild guild = manager.getByPlayer(actor.getUniqueId()).orElse(null);
        if (guild == null) {
            return ActionResult.NOT_IN_GUILD;
        }
        if (!isOfficerOrAbove(guild, actor.getUniqueId())) {
            return ActionResult.INSUFFICIENT_ROLE;
        }
        if (targetId.equals(guild.getLeaderId())) {
            return ActionResult.CANNOT_TARGET_LEADER;
        }
        manager.removeMember(guild, targetId);
        return ActionResult.OK;
    }

    public ActionResult setRole(Player leader, UUID targetId, GuildRole role) {
        Guild guild = manager.getByPlayer(leader.getUniqueId()).orElse(null);
        if (guild == null) {
            return ActionResult.NOT_IN_GUILD;
        }
        if (!guild.getLeaderId().equals(leader.getUniqueId())) {
            return ActionResult.INSUFFICIENT_ROLE;
        }
        guild.setRole(targetId, role);
        manager.persist(guild);
        return ActionResult.OK;
    }

    public ActionResult disband(Player leader) {
        Guild guild = manager.getByPlayer(leader.getUniqueId()).orElse(null);
        if (guild == null) {
            return ActionResult.NOT_IN_GUILD;
        }
        if (!guild.getLeaderId().equals(leader.getUniqueId())) {
            return ActionResult.INSUFFICIENT_ROLE;
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

    private boolean isOfficerOrAbove(Guild guild, UUID playerId) {
        GuildRole role = guild.roleOf(playerId);
        return role == GuildRole.LEADER || role == GuildRole.OFFICER;
    }
}
