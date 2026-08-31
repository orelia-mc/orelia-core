package rpg.extra.duel.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * One in-progress duel between two players - in-memory only (not persisted; a duel is
 * a short-lived interaction, same reasoning rpg.extra.party.model.Party has no DB backing
 * either - it's rebuilt fresh from PlayerData/quit events, there's nothing meaningful to
 * restore across a server restart).
 */
public final class DuelSession {

    private final UUID playerA;
    private final UUID playerB;
    private final Location returnLocationA;
    private final Location returnLocationB;
    private final int arenaIndex;

    public DuelSession(UUID playerA, UUID playerB, Location returnLocationA, Location returnLocationB, int arenaIndex) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.returnLocationA = returnLocationA;
        this.returnLocationB = returnLocationB;
        this.arenaIndex = arenaIndex;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    public Location getReturnLocation(UUID playerId) {
        if (playerId.equals(playerA)) {
            return returnLocationA;
        }
        if (playerId.equals(playerB)) {
            return returnLocationB;
        }
        throw new IllegalArgumentException(playerId + " is not part of this duel session");
    }

    /** The other participant's id - throws if {@code playerId} isn't part of this session, which would be a caller bug. */
    public UUID opponentOf(UUID playerId) {
        if (playerId.equals(playerA)) {
            return playerB;
        }
        if (playerId.equals(playerB)) {
            return playerA;
        }
        throw new IllegalArgumentException(playerId + " is not part of this duel session");
    }

    public boolean involves(UUID playerId) {
        return playerId.equals(playerA) || playerId.equals(playerB);
    }

    public int getArenaIndex() {
        return arenaIndex;
    }
}
