package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link Acquisition}: the NBT edge is a
 * fixpoint, the ladder is strictly ordered, and {@link #precedes} is
 * irreflexive. Each assertion is written to kill a specific mutant
 * (a flipped comparison, a swallowed default, a widened bound), not to
 * mirror the Javadoc.
 */
class AcquisitionMutationTest {

    @Test
    @DisplayName("fromNbtOrDefault ∘ toNbt is a fixpoint — every value re-reads as itself")
    void nbtRoundTripIsFixpoint() {
        for (Acquisition value : Acquisition.values()) {
            Acquisition reloaded = Acquisition.fromNbtOrDefault(value.toNbt());
            assertSame(value, reloaded,
                "toNbt→fromNbtOrDefault must reproduce " + value + " exactly");
            // Second pass through the same wire is stable.
            assertSame(value, Acquisition.fromNbtOrDefault(reloaded.toNbt()),
                "second pass through the wire is stable for " + value);
        }
    }

    @Test
    @DisplayName("null / empty / unrecognized NBT strings all collapse to FREE")
    void garbageNbtCollapsesToFree() {
        assertAll(
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault(null),
                "null string is the additive default"),
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault(""),
                "empty string is the additive default"),
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault("banana"),
                "unknown value is the additive default"),
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault("FREEBURG"),
                "prefix-garbage is not a value")
        );
    }

    @Test
    @DisplayName("fromNbtOrDefault is case-insensitive — lowercase persisted forms load")
    void lowercaseNbtLoads() {
        assertAll(
            () -> assertSame(Acquisition.CAPTURED, Acquisition.fromNbtOrDefault("captured")),
            () -> assertSame(Acquisition.ELEVATED, Acquisition.fromNbtOrDefault("elevated")),
            () -> assertSame(Acquisition.FOUNDED, Acquisition.fromNbtOrDefault("Founded"),
                "mixed case loads — the parse upper-cases"),
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault("free"))
        );
    }

    @Test
    @DisplayName("rank strictly increases by exactly one along the ladder")
    void rankIsStrictlyMonotonic() {
        Acquisition[] ladder = Acquisition.values();
        for (int i = 1; i < ladder.length; i++) {
            assertEquals(1, ladder[i].rank() - ladder[i - 1].rank(),
                "rank(" + ladder[i] + ") must be exactly rank(" + ladder[i - 1] + ") + 1");
        }
        assertEquals(0, ladder[0].rank(), "the ladder starts at rank 0");
    }

    @Test
    @DisplayName("precedes is irreflexive — no value precedes itself")
    void precedesIsIrreflexive() {
        for (Acquisition value : Acquisition.values()) {
            assertFalse(value.precedes(value),
                value + " must not precede itself (kills a '<=' mutant)");
        }
    }

    @Test
    @DisplayName("precedes is asymmetric — flipping the argument flips the answer")
    void precedesIsAsymmetric() {
        Acquisition[] ladder = Acquisition.values();
        for (int i = 0; i < ladder.length; i++) {
            for (int j = 0; j < ladder.length; j++) {
                boolean forward = ladder[i].precedes(ladder[j]);
                boolean backward = ladder[j].precedes(ladder[i]);
                assertTrue(forward != backward || i == j,
                    "precedes(" + ladder[i] + "," + ladder[j] + ") must be asymmetric off-diagonal");
            }
        }
    }

    @Test
    @DisplayName("precedes agrees with rank on every ordered pair")
    void precedesAgreesWithRank() {
        Acquisition[] ladder = Acquisition.values();
        for (int i = 0; i < ladder.length; i++) {
            for (int j = 0; j < ladder.length; j++) {
                assertEquals(ladder[i].rank() < ladder[j].rank(),
                    ladder[i].precedes(ladder[j]),
                    "precedes must be exactly rank < rank for ("
                        + ladder[i] + "," + ladder[j] + ")");
            }
        }
    }

    @Test
    @DisplayName("precedes rejects a null argument fast")
    void precedesRejectsNull() {
        assertThrows(NullPointerException.class, () -> Acquisition.FREE.precedes(null));
    }

    @Test
    @DisplayName("only FREE isDefault — every later rung knows it is not the default")
    void onlyFreeIsDefault() {
        for (Acquisition value : Acquisition.values()) {
            assertEquals(value == Acquisition.FREE, value.isDefault(),
                "isDefault must be true for FREE alone (got " + value + ")");
        }
    }

    @Test
    @DisplayName("toNbt emits the enum name — the wire form is uppercase and stable")
    void toNbtIsEnumName() {
        for (Acquisition value : Acquisition.values()) {
            assertEquals(value.name(), value.toNbt());
        }
    }

    @Test
    @DisplayName("toString never throws and is non-null for every rung")
    void toStringIsSafe() {
        for (Acquisition value : Acquisition.values()) {
            assertTrue(value.toString() != null && !value.toString().isEmpty(),
                "toString of " + value + " is non-empty");
        }
    }
}
