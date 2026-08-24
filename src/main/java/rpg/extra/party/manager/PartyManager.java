package rpg.extra.party.manager;

import rpg.extra.party.model.Party;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of every active {@link Party} and a reverse player-to-party index, plus
 * pending (unaccepted) invites.
 */
public final class PartyManager {

    private final Map<UUID, Party> partiesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToParty = new ConcurrentHashMap<>();
    /** invitee -> party id. One pending invite per player at a time. */
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();

    public Party create(UUID leaderId, int maxSize) {
        Party party = new Party(leaderId, maxSize);
        partiesById.put(party.getId(), party);
        playerToParty.put(leaderId, party.getId());
        return party;
    }

    public Optional<Party> getByPlayer(UUID playerId) {
        return Optional.ofNullable(playerToParty.get(playerId)).map(partiesById::get);
    }

    public void invite(UUID inviterId, UUID inviteeId) {
        getByPlayer(inviterId).ifPresent(party -> pendingInvites.put(inviteeId, party.getId()));
    }

    public Optional<Party> consumeInvite(UUID inviteeId) {
        UUID partyId = pendingInvites.remove(inviteeId);
        return partyId == null ? Optional.empty() : Optional.ofNullable(partiesById.get(partyId));
    }

    public void clearInvite(UUID inviteeId) {
        pendingInvites.remove(inviteeId);
    }

    public void joinParty(Party party, UUID playerId) {
        if (party.addMember(playerId)) {
            playerToParty.put(playerId, party.getId());
        }
    }

    public void leaveParty(UUID playerId) {
        getByPlayer(playerId).ifPresent(party -> {
            party.removeMember(playerId);
            playerToParty.remove(playerId);
            if (party.isEmpty()) {
                partiesById.remove(party.getId());
            } else if (party.getLeaderId().equals(playerId)) {
                party.setLeaderId(party.getMembers().iterator().next());
            }
        });
    }

    public void disband(Party party) {
        party.getMembers().forEach(playerToParty::remove);
        partiesById.remove(party.getId());
    }
}
