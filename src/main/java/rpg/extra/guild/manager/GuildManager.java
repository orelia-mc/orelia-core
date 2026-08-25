package rpg.extra.guild.manager;

import rpg.core.util.PendingQueue;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.repository.GuildRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory cache of every {@link Guild}, backed by {@link GuildRepository}. Guild counts
 * are small enough that keeping the whole roster in memory (write-through on every change)
 * is simpler than querying the database per lookup.
 */
public final class GuildManager {

    private final GuildRepository repository;
    private final Map<UUID, Guild> guildsById = new HashMap<>();
    private final Map<UUID, UUID> playerToGuild = new HashMap<>();
    /** invitee -> ordered guild ids - multiple guilds can invite the same player at once, oldest first. Was a plain (non-concurrent) single-slot Map before; PendingQueue is internally concurrent. */
    private final PendingQueue<UUID> pendingInvites = new PendingQueue<>();

    public GuildManager(GuildRepository repository) {
        this.repository = repository;
    }

    public void loadAll() {
        guildsById.clear();
        playerToGuild.clear();
        for (Guild guild : repository.loadAll()) {
            guildsById.put(guild.getId(), guild);
            guild.getMembers().keySet().forEach(member -> playerToGuild.put(member, guild.getId()));
        }
    }

    public Guild create(String name, String tag, UUID leaderId) {
        Guild guild = Guild.create(name, tag, leaderId);
        guildsById.put(guild.getId(), guild);
        playerToGuild.put(leaderId, guild.getId());
        repository.save(guild);
        return guild;
    }

    public Optional<Guild> getByPlayer(UUID playerId) {
        return Optional.ofNullable(playerToGuild.get(playerId)).map(guildsById::get);
    }

    public Optional<Guild> getById(UUID guildId) {
        return Optional.ofNullable(guildsById.get(guildId));
    }

    public Collection<Guild> getAll() {
        return List.copyOf(guildsById.values());
    }

    public void invite(UUID guildId, UUID inviteeId) {
        pendingInvites.add(inviteeId, guildId);
    }

    /** Looks at the oldest pending invite without consuming it - lets the GUI show an accept/decline prompt before the player types anything. */
    public Optional<Guild> peekInvite(UUID inviteeId) {
        return pendingInvites.peekOldest(inviteeId).map(guildsById::get);
    }

    /** Every guild currently inviting {@code inviteeId}, oldest first - for the GUI's ordered pending-invites list. */
    public List<Guild> peekAllInvites(UUID inviteeId) {
        List<Guild> guilds = new ArrayList<>();
        for (UUID guildId : pendingInvites.peekAll(inviteeId)) {
            Guild guild = guildsById.get(guildId);
            if (guild != null) {
                guilds.add(guild);
            }
        }
        return guilds;
    }

    public Optional<Guild> consumeInvite(UUID inviteeId) {
        return pendingInvites.consumeOldest(inviteeId).map(guildsById::get);
    }

    /** Consumes one specific guild's invite (not necessarily the oldest). */
    public Optional<Guild> consumeInvite(UUID inviteeId, UUID guildId) {
        return pendingInvites.consume(inviteeId, guildId).map(guildsById::get);
    }

    public void clearInvite(UUID inviteeId) {
        pendingInvites.clear(inviteeId);
    }

    public void persist(Guild guild) {
        repository.save(guild);
    }

    public void addMember(Guild guild, UUID playerId, String roleId) {
        guild.addMember(playerId, roleId);
        playerToGuild.put(playerId, guild.getId());
        repository.save(guild);
    }

    public void removeMember(Guild guild, UUID playerId) {
        guild.removeMember(playerId);
        playerToGuild.remove(playerId);
        if (guild.getMembers().isEmpty()) {
            disband(guild);
        } else {
            repository.save(guild);
        }
    }

    public void disband(Guild guild) {
        guild.getMembers().keySet().forEach(playerToGuild::remove);
        guildsById.remove(guild.getId());
        repository.delete(guild.getId());
    }
}
