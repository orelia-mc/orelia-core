package rpg.extra.api;

import rpg.extra.party.model.Party;
import rpg.extra.party.service.PartyService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class PartyApiImpl implements PartyApi {

    private final PartyService partyService;

    PartyApiImpl(PartyService partyService) {
        this.partyService = partyService;
    }

    @Override
    public boolean isInParty(UUID playerId) {
        return partyService.getParty(playerId).isPresent();
    }

    @Override
    public Optional<UUID> getLeaderId(UUID playerId) {
        return partyService.getParty(playerId).map(Party::getLeaderId);
    }

    @Override
    public Set<UUID> getMemberIds(UUID playerId) {
        return partyService.getParty(playerId).map(Party::getMembers).orElse(Set.of());
    }
}
