package rpg.extra.guild.manager;

import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRole;
import rpg.extra.guild.repository.GuildRepository;

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
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

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
        pendingInvites.put(inviteeId, guildId);
    }

    public Optional<Guild> consumeInvite(UUID inviteeId) {
        UUID guildId = pendingInvites.remove(inviteeId);
        return guildId == null ? Optional.empty() : Optional.ofNullable(guildsById.get(guildId));
    }

    public void clearInvite(UUID inviteeId) {
        pendingInvites.remove(inviteeId);
    }

    public void persist(Guild guild) {
        repository.save(guild);
    }

    public void addMember(Guild guild, UUID playerId, GuildRole role) {
        guild.addMember(playerId, role);
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
