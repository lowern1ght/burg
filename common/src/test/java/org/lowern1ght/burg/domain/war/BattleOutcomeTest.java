package org.lowern1ght.burg.domain.war;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The battle summary record, in pure JUnit. The optional casualty counts
 * and their validation are the whole surface — the in-game state machine
 * that will feed this type is out of scope by design.
 */
class BattleOutcomeTest {

    @Test
    @DisplayName("decided carries the winner without casualty bookkeeping")
    void decidedHasNoCounts() {
        var outcome = BattleOutcome.decided(true);
        assertTrue(outcome.attackerWins());
        assertFalse(outcome.attackerCasualties().isPresent());
        assertFalse(outcome.defenderCasualties().isPresent());
    }

    @Test
    @DisplayName("counted carries both tolls, zeros included")
    void countedCarriesBoth() {
        var outcome = BattleOutcome.counted(false, 3, 0);
        assertFalse(outcome.attackerWins());
        assertEquals(OptionalInt.of(3), outcome.attackerCasualties());
        assertEquals(OptionalInt.of(0), outcome.defenderCasualties());
    }

    @Test
    @DisplayName("negative casualties are rejected at construction")
    void negativeCasualtiesRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> BattleOutcome.counted(true, -1, 0));
        assertThrows(IllegalArgumentException.class,
            () -> BattleOutcome.counted(true, 0, -2));
    }

    @Test
    @DisplayName("null optionals are rejected at construction")
    void nullOptionalsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new BattleOutcome(true, null, OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class,
            () -> new BattleOutcome(true, OptionalInt.empty(), null));
    }

    @Test
    @DisplayName("value equality follows the components")
    void valueEquality() {
        assertEquals(BattleOutcome.counted(true, 1, 2), BattleOutcome.counted(true, 1, 2));
        assertEquals(BattleOutcome.decided(false), BattleOutcome.decided(false));
    }
}
