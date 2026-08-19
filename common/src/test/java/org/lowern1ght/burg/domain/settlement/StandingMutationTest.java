package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link Standing}: the delta algebra is
 * an identity at zero, deltas cancel, and the zero-read constant matches
 * the constructor default. Assertions target mutants like {@code withDelta}
 * ignoring its argument or {@code isZero} comparing against the wrong
 * boundary.
 */
class StandingMutationTest {

    private static CitizenId citizen(String tail) {
        return CitizenId.of(UUID.nameUUIDFromBytes(tail.getBytes()));
    }

    @Test
    @DisplayName("withDelta(0) is the identity — equals the original standing")
    void withDeltaZeroIsIdentity() {
        Standing standing = new Standing(citizen("alice"), 5);

        assertEquals(standing, standing.withDelta(0),
            "a zero delta must produce an equal standing (value and citizen)");
        assertEquals(5, standing.withDelta(0).value(),
            "the score is untouched by a zero delta");
    }

    @Test
    @DisplayName("withDelta cancels — +5 then -5 restores the original standing")
    void withDeltaCancels() {
        Standing standing = new Standing(citizen("alice"), 7);

        assertEquals(standing, standing.withDelta(5).withDelta(-5),
            "opposing deltas must cancel exactly");
        assertEquals(standing, standing.withDelta(-5).withDelta(5),
            "cancellation is order-independent for plain integers");
    }

    @Test
    @DisplayName("withValue sets the score and keeps the citizen")
    void withValueKeepsCitizen() {
        CitizenId alice = citizen("alice");
        Standing standing = new Standing(alice, 5);
        Standing reset = standing.withValue(-3);

        assertAll(
            () -> assertEquals(-3, reset.value()),
            () -> assertEquals(alice, reset.citizen())
        );
    }

    @Test
    @DisplayName("isZero is exactly the value==0 boundary")
    void isZeroBoundary() {
        CitizenId alice = citizen("alice");
        assertAll(
            () -> assertTrue(new Standing(alice, 0).isZero(), "0 is zero"),
            () -> assertFalse(new Standing(alice, 1).isZero(), "1 is not zero"),
            () -> assertFalse(new Standing(alice, -1).isZero(),
                "-1 is not zero — negative standing is real and persists"),
            () -> assertTrue(new Standing(alice, 2).withDelta(-2).isZero(),
                "a delta can drive an entry back to zero")
        );
    }

    @Test
    @DisplayName("DEFAULT is 0 and ZERO reads DEFAULT on the EMPTY citizen")
    void zeroSentinelShape() {
        assertAll(
            () -> assertEquals(0, Standing.DEFAULT),
            () -> assertEquals(Standing.DEFAULT, Standing.ZERO.value()),
            () -> assertEquals(CitizenId.EMPTY, Standing.ZERO.citizen()),
            () -> assertTrue(Standing.ZERO.isZero())
        );
    }

    @Test
    @DisplayName("equality is structural — same score different citizen is a different standing")
    void equalityIsStructural() {
        Standing aliceFive = new Standing(citizen("alice"), 5);
        Standing bobFive = new Standing(citizen("bob"), 5);
        Standing aliceSix = new Standing(citizen("alice"), 6);

        assertAll(
            () -> assertEquals(aliceFive, new Standing(citizen("alice"), 5)),
            () -> assertNotEquals(aliceFive, bobFive, "the citizen discriminates"),
            () -> assertNotEquals(aliceFive, aliceSix, "the score discriminates")
        );
    }

    @Test
    @DisplayName("a null citizen is rejected at construction")
    void nullCitizenRejected() {
        assertThrows(NullPointerException.class, () -> new Standing(null, 5));
    }

    @Test
    @DisplayName("negative scores are representable — no clamping in the domain type")
    void negativeScoresAllowed() {
        Standing fallen = new Standing(citizen("alice"), -7);
        assertEquals(-7, fallen.value(),
            "the domain stores what it is given; bucketing is a read-side concern");
    }
}
