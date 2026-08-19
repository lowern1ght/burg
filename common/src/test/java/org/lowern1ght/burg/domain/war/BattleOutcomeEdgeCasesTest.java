package org.lowern1ght.burg.domain.war;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester edge cases for {@link BattleOutcome}: swapped-argument-order
 * sensitivity (attacker toll vs defender toll), the zero-vs-empty
 * distinction, and boundary casualty counts.
 */
class BattleOutcomeEdgeCasesTest {

    @Test
    @DisplayName("swapping the casualty arguments changes the outcome — they are positional, not a set")
    void casualtyArgumentOrderMatters() {
        BattleOutcome attackerHeavy = BattleOutcome.counted(true, 9, 1);
        BattleOutcome defenderHeavy = BattleOutcome.counted(true, 1, 9);

        assertAll(
            () -> assertNotEquals(attackerHeavy, defenderHeavy,
                "3 attacker / 1 defender is a different battle than 1 attacker / 3 defender"),
            () -> assertEquals(9, attackerHeavy.attackerCasualties().getAsInt()),
            () -> assertEquals(1, attackerHeavy.defenderCasualties().getAsInt())
        );
    }

    @Test
    @DisplayName("zero casualties are counted data, not missing data — zero != empty")
    void zeroIsNotEmpty() {
        BattleOutcome bloodless = BattleOutcome.counted(false, 0, 0);
        BattleOutcome uncounted = BattleOutcome.decided(false);

        assertAll(
            () -> assertTrue(bloodless.attackerCasualties().isPresent(),
                "a counted zero is present"),
            () -> assertTrue(bloodless.defenderCasualties().isPresent()),
            () -> assertEquals(OptionalInt.of(0), bloodless.attackerCasualties()),
            () -> assertNotEquals(bloodless, uncounted,
                "'fought with no losses' and 'did not count' are different facts")
        );
    }

    @Test
    @DisplayName("boundary casualty counts — Integer.MAX_VALUE is a legal toll")
    void boundaryCounts() {
        BattleOutcome apocalyptic = BattleOutcome.counted(true, Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertAll(
            () -> assertEquals(Integer.MAX_VALUE, apocalyptic.attackerCasualties().getAsInt()),
            () -> assertEquals(Integer.MAX_VALUE, apocalyptic.defenderCasualties().getAsInt())
        );
    }

    @Test
    @DisplayName("one side counted and the other empty is a legal mixed shape")
    void mixedCountedAndEmpty() {
        BattleOutcome mixed = new BattleOutcome(
            true, OptionalInt.of(3), OptionalInt.empty());

        assertAll(
            () -> assertTrue(mixed.attackerCasualties().isPresent()),
            () -> assertFalse(mixed.defenderCasualties().isPresent()),
            () -> assertNotEquals(BattleOutcome.decided(true), mixed,
                "a half-counted outcome differs from a decided one")
        );
    }

    @Test
    @DisplayName("hashCode is consistent with equals across factory-built equal instances")
    void hashCodeConsistency() {
        BattleOutcome left = BattleOutcome.counted(true, 2, 5);
        BattleOutcome right = new BattleOutcome(true, OptionalInt.of(2), OptionalInt.of(5));

        assertAll(
            () -> assertEquals(left, right,
                "factory and canonical construction of the same battle are equal"),
            () -> assertEquals(left.hashCode(), right.hashCode(),
                "equal battles hash equally"),
            () -> assertEquals(BattleOutcome.decided(false).hashCode(),
                BattleOutcome.decided(false).hashCode())
        );
    }

    @Test
    @DisplayName("negative boundary tolls are rejected — MIN_VALUE included")
    void negativeBoundariesRejected() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> BattleOutcome.counted(true, Integer.MIN_VALUE, 0)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> BattleOutcome.counted(true, 0, Integer.MIN_VALUE)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new BattleOutcome(true, OptionalInt.of(-1), OptionalInt.empty()))
        );
    }
}
