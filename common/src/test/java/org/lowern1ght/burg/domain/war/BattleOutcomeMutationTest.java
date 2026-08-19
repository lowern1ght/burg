package org.lowern1ght.burg.domain.war;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link BattleOutcome}: the
 * decided/counted factory split (empty vs zero), the non-negative
 * guarantee, and the null-OptionalInt guard.
 */
class BattleOutcomeMutationTest {

    @Test
    @DisplayName("decided() knows the winner without counting bodies")
    void decidedHasNoCounts() {
        BattleOutcome win = BattleOutcome.decided(true);
        BattleOutcome loss = BattleOutcome.decided(false);

        assertAll(
            () -> assertTrue(win.attackerWins()),
            () -> assertFalse(loss.attackerWins()),
            () -> assertFalse(win.attackerCasualties().isPresent(),
                "an auto-resolved engagement carries no attacker toll"),
            () -> assertFalse(win.defenderCasualties().isPresent(),
                "…and no defender toll"),
            () -> assertFalse(loss.defenderCasualties().isPresent())
        );
    }

    @Test
    @DisplayName("counted() reports zeros as present — a bloodless engagement is not an uncounted one")
    void countedZerosArePresent() {
        BattleOutcome bloodless = BattleOutcome.counted(true, 0, 0);

        assertAll(
            () -> assertTrue(bloodless.attackerWins()),
            () -> assertTrue(bloodless.attackerCasualties().isPresent(),
                "zero is a counted value, not an empty"),
            () -> assertEquals(0, bloodless.attackerCasualties().getAsInt()),
            () -> assertTrue(bloodless.defenderCasualties().isPresent()),
            () -> assertEquals(0, bloodless.defenderCasualties().getAsInt())
        );
    }

    @Test
    @DisplayName("counted() carries both sides' tolls exactly")
    void countedCarriesTolls() {
        BattleOutcome battle = BattleOutcome.counted(false, 3, 7);

        assertAll(
            () -> assertFalse(battle.attackerWins()),
            () -> assertEquals(3, battle.attackerCasualties().getAsInt()),
            () -> assertEquals(7, battle.defenderCasualties().getAsInt())
        );
    }

    @Test
    @DisplayName("negative casualty counts are a construction-site bug and throw")
    void negativeCountsRejected() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> BattleOutcome.counted(true, -1, 0)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> BattleOutcome.counted(true, 0, -1)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new BattleOutcome(true, OptionalInt.of(-5), OptionalInt.empty()))
        );
    }

    @Test
    @DisplayName("a null OptionalInt is rejected — emptiness must be explicit")
    void nullOptionalsRejected() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> new BattleOutcome(true, null, OptionalInt.empty())),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new BattleOutcome(true, OptionalInt.empty(), null))
        );
    }

    @Test
    @DisplayName("equality is structural — two identical decisions are one outcome")
    void structuralEquality() {
        assertEquals(BattleOutcome.decided(true), BattleOutcome.decided(true));
        assertEquals(BattleOutcome.counted(false, 1, 2), BattleOutcome.counted(false, 1, 2));
    }
}
