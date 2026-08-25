package rpg.extra.party.manager;

import rpg.core.util.PendingQueue;
import rpg.extra.party.model.Party;

import java.util.ArrayList;
import java.util.List;
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
    /** invitee -> ordered party ids - multiple parties can invite the same player at once, oldest first. */
    private final PendingQueue<UUID> pendingInvites = new PendingQueue<>();

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
        getByPlayer(inviterId).ifPresent(party -> pendingInvites.add(inviteeId, party.getId()));
    }

    /** Looks at the oldest pending invite without consuming it - lets the GUI show an accept/decline prompt before the player types anything. */
    public Optional<Party> peekInvite(UUID inviteeId) {
        return pendingInvites.peekOldest(inviteeId).map(partiesById::get);
    }

    /** Every party currently inviting {@code inviteeId}, oldest first - for the GUI's ordered pending-invites list. */
    public List<Party> peekAllInvites(UUID inviteeId) {
        List<Party> parties = new ArrayList<>();
        for (UUID partyId : pendingInvites.peekAll(inviteeId)) {
            Party party = partiesById.get(partyId);
            if (party != null) {
                parties.add(party);
            }
        }
        return parties;
    }

    public Optional<Party> consumeInvite(UUID inviteeId) {
        return pendingInvites.consumeOldest(inviteeId).map(partiesById::get);
    }

    /** Consumes one specific party's invite (not necessarily the oldest). */
    public Optional<Party> consumeInvite(UUID inviteeId, UUID partyId) {
        return pendingInvites.consume(inviteeId, partyId).map(partiesById::get);
    }

    public void clearInvite(UUID inviteeId) {
        pendingInvites.clear(inviteeId);
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
