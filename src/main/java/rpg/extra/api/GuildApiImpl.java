package rpg.extra.api;

import rpg.extra.guild.model.Guild;
import rpg.extra.guild.service.GuildService;

import java.util.Optional;
import java.util.UUID;

final class GuildApiImpl implements GuildApi {

    private final GuildService guildService;

    GuildApiImpl(GuildService guildService) {
        this.guildService = guildService;
    }

    @Override
    public Optional<String> getGuildName(UUID playerId) {
        return guildService.getGuild(playerId).map(Guild::getName);
    }

    @Override
    public Optional<String> getGuildTag(UUID playerId) {
        return guildService.getGuild(playerId).map(Guild::getTag);
    }
}
