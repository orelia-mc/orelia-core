package rpg.extra.duel.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelArenaAllocatorTest {

    @Test
    void returnsFirstFreeIndexWhenNoneOccupied() {
        assertEquals(Optional.of(0), DuelArenaAllocator.findFreeIndex(3, Set.of()));
    }

    @Test
    void skipsOccupiedIndices() {
        assertEquals(Optional.of(1), DuelArenaAllocator.findFreeIndex(3, Set.of(0)));
        assertEquals(Optional.of(2), DuelArenaAllocator.findFreeIndex(3, Set.of(0, 1)));
    }

    @Test
    void emptyWhenAllArenasOccupied() {
        assertTrue(DuelArenaAllocator.findFreeIndex(2, Set.of(0, 1)).isEmpty());
    }

    @Test
    void emptyWhenNoArenasConfigured() {
        assertTrue(DuelArenaAllocator.findFreeIndex(0, Set.of()).isEmpty());
    }
}
