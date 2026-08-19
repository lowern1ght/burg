package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.ItemId;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link ProductionRule}: the due-check
 * arithmetic (multiples, including tick 0) and the constructor
 * boundaries. Kills mutants like {@code gameTime % everyTicks == 0}
 * becoming {@code != 0}, or an {@code amount <= 0} check losing its
 * equals.
 */
class ProductionRuleMutationTest {

    private static final ItemId BREAD = ItemId.of("minecraft:bread");

    private static ProductionRule rule(int amount, long everyTicks) {
        return new ProductionRule(BREAD, amount, everyTicks, 64);
    }

    @Test
    @DisplayName("tick 0 is due for every cadence — 0 % n == 0")
    void tickZeroIsDue() {
        assertAll(
            () -> assertTrue(rule(1, 1).isDue(0)),
            () -> assertTrue(rule(1, 7).isDue(0), "0 is a multiple of every cadence")
        );
    }

    @Test
    @DisplayName("isDue fires exactly on multiples of the cadence")
    void isDueOnMultiplesOnly() {
        ProductionRule weekly = rule(2, 7);

        assertAll(
            () -> assertTrue(weekly.isDue(7)),
            () -> assertTrue(weekly.isDue(14)),
            () -> assertTrue(weekly.isDue(21)),
            () -> assertFalse(weekly.isDue(6), "one before the first multiple is not due"),
            () -> assertFalse(weekly.isDue(8), "one after the first multiple is not due"),
            () -> assertFalse(weekly.isDue(13)),
            () -> assertFalse(weekly.isDue(48))
        );
    }

    @Test
    @DisplayName("a cadence-1 rule is due on every tick")
    void everyTickOneIsAlwaysDue() {
        ProductionRule every = rule(1, 1);

        for (long t = 0; t < 10; t++) {
            assertTrue(every.isDue(t), "tick " + t + " is due for everyTicks=1");
        }
    }

    @Test
    @DisplayName("constructor boundaries: amount/everyTicks must be positive, capacity non-negative")
    void constructorBoundaries() {
        assertAll(
            () -> assertEquals(1, rule(1, 1).amount(), "amount=1 is the minimum valid"),
            () -> assertEquals(1, rule(1, 1).everyTicks(), "everyTicks=1 is the minimum valid"),
            () -> assertThrows(IllegalArgumentException.class, () -> rule(0, 10),
                "amount=0 throws"),
            () -> assertThrows(IllegalArgumentException.class, () -> rule(-1, 10),
                "negative amount throws"),
            () -> assertThrows(IllegalArgumentException.class, () -> rule(1, 0),
                "everyTicks=0 throws"),
            () -> assertThrows(IllegalArgumentException.class, () -> rule(1, -10),
                "negative cadence throws"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new ProductionRule(BREAD, 1, 10, -1),
                "negative capacity throws"),
            () -> assertEquals(0, new ProductionRule(BREAD, 1, 10, 0).capacityItems(),
                "capacity=0 (no storage) is valid")
        );
    }

    @Test
    @DisplayName("a null output is rejected at construction")
    void nullOutputRejected() {
        assertThrows(NullPointerException.class,
            () -> new ProductionRule(null, 1, 10, 64));
    }

    @Test
    @DisplayName("isActiveCadence is exactly the positive-integer gate")
    void isActiveCadenceGate() {
        assertAll(
            () -> assertTrue(ProductionRule.isActiveCadence(1)),
            () -> assertTrue(ProductionRule.isActiveCadence(600)),
            () -> assertFalse(ProductionRule.isActiveCadence(0),
                "zero cadence short-circuits the legacy tick loop"),
            () -> assertFalse(ProductionRule.isActiveCadence(-5))
        );
    }
}
