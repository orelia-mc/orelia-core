package rpg.extra.party.service;

import org.bukkit.entity.Player;
import rpg.extra.party.manager.PartyManager;
import rpg.extra.party.model.Party;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Party business rules on top of {@link PartyManager}'s plain data operations: who may
 * invite/kick/disband, and the player-facing outcomes.
 */
public final class PartyService {

    public enum ActionResult {
        OK, ALREADY_IN_PARTY, NOT_IN_PARTY, NOT_LEADER, PARTY_FULL, TARGET_ALREADY_IN_PARTY, NO_PENDING_INVITE,
        CANNOT_TARGET_SELF, LEADER_MUST_DISBAND
    }

    private final PartyManager manager;
    private final int maxPartySize;

    public PartyService(PartyManager manager, int maxPartySize) {
        this.manager = manager;
        this.maxPartySize = maxPartySize;
    }

    public ActionResult create(Player leader) {
        if (manager.getByPlayer(leader.getUniqueId()).isPresent()) {
            return ActionResult.ALREADY_IN_PARTY;
        }
        manager.create(leader.getUniqueId(), maxPartySize);
        return ActionResult.OK;
    }

    public ActionResult invite(Player inviter, Player invitee) {
        if (inviter.getUniqueId().equals(invitee.getUniqueId())) {
            return ActionResult.CANNOT_TARGET_SELF;
        }
        Party party = manager.getByPlayer(inviter.getUniqueId()).orElse(null);
        if (party == null) {
            return ActionResult.NOT_IN_PARTY;
        }
        if (!party.getLeaderId().equals(inviter.getUniqueId())) {
            return ActionResult.NOT_LEADER;
        }
        if (manager.getByPlayer(invitee.getUniqueId()).isPresent()) {
            return ActionResult.TARGET_ALREADY_IN_PARTY;
        }
        if (party.isFull()) {
            return ActionResult.PARTY_FULL;
        }
        manager.invite(inviter.getUniqueId(), invitee.getUniqueId());
        return ActionResult.OK;
    }

    /** Accepts the oldest (first-received) pending invite. */
    public ActionResult accept(Player invitee) {
        return accept(invitee, manager.peekInvite(invitee.getUniqueId()).map(Party::getId).orElse(null));
    }

    /** Accepts a specific party's invite (not necessarily the oldest) - {@code partyId} null-safe. */
    public ActionResult accept(Player invitee, UUID partyId) {
        if (partyId == null) {
            return ActionResult.NO_PENDING_INVITE;
        }
        Optional<Party> party = manager.consumeInvite(invitee.getUniqueId(), partyId);
        if (party.isEmpty()) {
            return ActionResult.NO_PENDING_INVITE;
        }
        if (party.get().isFull()) {
            return ActionResult.PARTY_FULL;
        }
        manager.joinParty(party.get(), invitee.getUniqueId());
        return ActionResult.OK;
    }

    /** Declines the oldest (first-received) pending invite. */
    public ActionResult decline(Player invitee) {
        return decline(invitee, manager.peekInvite(invitee.getUniqueId()).map(Party::getId).orElse(null));
    }

    /** Declines a specific party's invite (not necessarily the oldest). */
    public ActionResult decline(Player invitee, UUID partyId) {
        if (partyId == null) {
            return ActionResult.NO_PENDING_INVITE;
        }
        return manager.consumeInvite(invitee.getUniqueId(), partyId).isPresent()
                ? ActionResult.OK : ActionResult.NO_PENDING_INVITE;
    }

    public ActionResult leave(Player player) {
        Party party = manager.getByPlayer(player.getUniqueId()).orElse(null);
        if (party == null) {
            return ActionResult.NOT_IN_PARTY;
        }
        if (party.getLeaderId().equals(player.getUniqueId())) {
            return ActionResult.LEADER_MUST_DISBAND;
        }
        manager.leaveParty(player.getUniqueId());
        return ActionResult.OK;
    }

    /**
     * Result of {@link #leaveOnQuit}: the party as it stands right after the disconnecting
     * player was removed (empty when the party was disbanded outright), and whether leadership
     * was auto-transferred as part of the removal.
     */
    public record QuitLeaveOutcome(Party party, boolean leadershipTransferred) {
    }

    /**
     * Leaves {@code playerId}'s party as a side effect of them disconnecting, bypassing the
     * {@link ActionResult#LEADER_MUST_DISBAND} restriction that applies to an explicit
     * {@code /party leave}: a disconnecting leader hands off leadership to another member
     * (auto-picked by {@link rpg.extra.party.manager.PartyManager#leaveParty}) if any remain, or
     * the party is disbanded if they were the last member. Returns empty when the player wasn't
     * in a party, or the party had no other members left to notify.
     */
    public Optional<QuitLeaveOutcome> leaveOnQuit(UUID playerId) {
        Party party = manager.getByPlayer(playerId).orElse(null);
        if (party == null || party.getMembers().size() <= 1) {
            if (party != null) {
                manager.leaveParty(playerId);
            }
            return Optional.empty();
        }
        boolean wasLeader = party.getLeaderId().equals(playerId);
        manager.leaveParty(playerId);
        return Optional.of(new QuitLeaveOutcome(party, wasLeader));
    }

    /** Hands leadership to {@code newLeaderId} (must already be a member). */
    public ActionResult transferLeadership(Player currentLeader, UUID newLeaderId) {
        Party party = manager.getByPlayer(currentLeader.getUniqueId()).orElse(null);
        if (party == null) {
            return ActionResult.NOT_IN_PARTY;
        }
        if (!party.getLeaderId().equals(currentLeader.getUniqueId())) {
            return ActionResult.NOT_LEADER;
        }
        if (!party.getMembers().contains(newLeaderId)) {
            return ActionResult.NOT_IN_PARTY;
        }
        party.setLeaderId(newLeaderId);
        return ActionResult.OK;
    }

    public ActionResult kick(Player leader, UUID targetId) {
        if (leader.getUniqueId().equals(targetId)) {
            return ActionResult.CANNOT_TARGET_SELF;
        }
        Party party = manager.getByPlayer(leader.getUniqueId()).orElse(null);
        if (party == null) {
            return ActionResult.NOT_IN_PARTY;
        }
        if (!party.getLeaderId().equals(leader.getUniqueId())) {
            return ActionResult.NOT_LEADER;
        }
        if (!party.getMembers().contains(targetId)) {
            return ActionResult.NOT_IN_PARTY;
        }
        manager.leaveParty(targetId);
        return ActionResult.OK;
    }

    public ActionResult disband(Player leader) {
        Party party = manager.getByPlayer(leader.getUniqueId()).orElse(null);
        if (party == null) {
            return ActionResult.NOT_IN_PARTY;
        }
        if (!party.getLeaderId().equals(leader.getUniqueId())) {
            return ActionResult.NOT_LEADER;
        }
        manager.disband(party);
        return ActionResult.OK;
    }

    /** The oldest party {@code playerId} has a pending invite to, if any - for the GUI's accept/decline prompt. */
    public Optional<Party> peekPendingInvite(UUID playerId) {
        return manager.peekInvite(playerId);
    }

    /** Every party currently inviting {@code playerId}, oldest first - for the GUI's ordered pending-invites list. */
    public List<Party> peekAllPendingInvites(UUID playerId) {
        return manager.peekAllInvites(playerId);
    }

    public Optional<Party> getParty(UUID playerId) {
        return manager.getByPlayer(playerId);
    }
}
