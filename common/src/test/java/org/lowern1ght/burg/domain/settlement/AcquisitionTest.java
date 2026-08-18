package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four-step Acquisition ladder, in pure JUnit. Bare JVM, no Minecraft —
 * the enum is the additive NBT default for old saves, so the boundaries
 * around FREE are exactly the kind of detail that is silent in a unit run
 * and noisy in the engine tick.
 */
class AcquisitionTest {

    @Test
    @DisplayName("the additive default for old saves is FREE")
    void oldSavesDefaultToFree() {
        assertAll(
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault(null),
                "null reads as FREE"),
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault(""),
                "empty string reads as FREE"),
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault("FOOBAR"),
                "unknown names read as FREE (forward-compat with newer enum members)"),
            () -> assertSame(Acquisition.FREE, Acquisition.fromNbtOrDefault("free"),
                "lower-case is normalised before lookup")
        );
    }

    @Test
    @DisplayName("the four named values round-trip through NBT")
    void roundTrip() {
        for (Acquisition a : Acquisition.values()) {
            assertSame(a, Acquisition.fromNbtOrDefault(a.toNbt()),
                a.name() + " survives the string round-trip");
        }
    }

    @Test
    @DisplayName("the ladder is ordered FREE → ELEVATED → FOUNDED → CAPTURED")
    void ladder() {
        assertAll(
            () -> assertEquals(0, Acquisition.FREE.rank()),
            () -> assertEquals(1, Acquisition.ELEVATED.rank()),
            () -> assertEquals(2, Acquisition.FOUNDED.rank()),
            () -> assertEquals(3, Acquisition.CAPTURED.rank()),
            () -> assertTrue(Acquisition.FREE.isDefault()),
            () -> assertFalse(Acquisition.ELEVATED.isDefault()),
            () -> assertFalse(Acquisition.FOUNDED.isDefault()),
            () -> assertFalse(Acquisition.CAPTURED.isDefault())
        );
    }

    @Test
    @DisplayName("precedes is strict-less-than along the rank")
    void precedesIsStrict() {
        assertAll(
            () -> assertTrue(Acquisition.FREE.precedes(Acquisition.ELEVATED)),
            () -> assertTrue(Acquisition.ELEVATED.precedes(Acquisition.FOUNDED)),
            () -> assertTrue(Acquisition.FOUNDED.precedes(Acquisition.CAPTURED)),
            () -> assertFalse(Acquisition.FREE.precedes(Acquisition.FREE),
                "a value does not precede itself"),
            () -> assertFalse(Acquisition.FOUNDED.precedes(Acquisition.ELEVATED),
                "the ladder only goes up")
        );
    }
}