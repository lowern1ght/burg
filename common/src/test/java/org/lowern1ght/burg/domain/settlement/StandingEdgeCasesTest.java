package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester edge cases for {@link Standing}: the int-score boundaries, the
 * overflow behaviour of {@link #withDelta(int)}, and the ZERO sentinel's
 * citizen identity (a subtle trap: ZERO carries the EMPTY citizen, so it
 * is not equal to a zero score for a real citizen).
 */
class StandingEdgeCasesTest {

    private static final CitizenId CITIZEN = CitizenId.of(new UUID(0x42L, 1L));
    private static final CitizenId OTHER = CitizenId.of(new UUID(0x42L, 2L));

    @Test
    @DisplayName("DOCUMENTED LATENT BUG: withDelta overflows int and wraps the score")
    void documentedLatentBug_withDeltaOverflowWraps() {
        // value + delta is plain int arithmetic. A standing at MAX_VALUE
        // that earns +1 wraps to MIN_VALUE — the luckiest citizen becomes
        // the most despised one in a single tick. Characterisation of the
        // current wrap; the fix (Math.addExact or clamping) should flip
        // this test.
        Standing maxed = new Standing(CITIZEN, Integer.MAX_VALUE);

        Standing wrapped = maxed.withDelta(1);

        assertTrue(wrapped.value() < 0,
            "LATENT BUG pinned: MAX + 1 wraps to " + wrapped.value());
    }

    @Test
    @DisplayName("boundary scores survive construction: MIN, MAX, -1, 0")
    void boundaryScores() {
        assertAll(
            () -> assertEquals(Integer.MAX_VALUE, new Standing(CITIZEN, Integer.MAX_VALUE).value()),
            () -> assertEquals(Integer.MIN_VALUE, new Standing(CITIZEN, Integer.MIN_VALUE).value()),
            () -> assertEquals(-1, new Standing(CITIZEN, -1).value())
        );
    }

    @Test
    @DisplayName("ZERO carries the EMPTY citizen — it is not a zero score for a real citizen")
    void zeroSentinelIdentity() {
        Standing realZero = new Standing(CITIZEN, Standing.DEFAULT);

        assertAll(
            () -> assertSame(CitizenId.EMPTY, Standing.ZERO.citizen(),
                "the sentinel is the EMPTY citizen's zero"),
            () -> assertNotEquals(Standing.ZERO, realZero,
                "a real citizen's zero is a different fact than the sentinel"),
            () -> assertTrue(realZero.isZero()),
            () -> assertTrue(Standing.ZERO.isZero()),
            () -> assertNotEquals(new Standing(CITIZEN, 0), new Standing(OTHER, 0),
                "the citizen is part of equality — two zero scores differ")
        );
    }

    @Test
    @DisplayName("withValue replaces the score; withDelta(0) is a value-preserving copy")
    void mutators() {
        Standing base = new Standing(CITIZEN, 10);

        assertAll(
            () -> assertEquals(99, base.withValue(99).value()),
            () -> assertEquals(10, base.withDelta(0).value(),
                "a zero delta keeps the score"),
            () -> assertNotEquals(base, base.withValue(99)),
            () -> assertEquals(base, base.withValue(10),
                "rewriting the same score yields an equal standing"),
            () -> assertEquals(base.hashCode(), base.withValue(10).hashCode())
        );
    }

    @Test
    @DisplayName("a null citizen is rejected at construction")
    void nullCitizenRejected() {
        assertThrows(NullPointerException.class, () -> new Standing(null, 5));
    }

    @Test
    @DisplayName("the citizen survives every mutation — withDelta never swaps the identity")
    void citizenIsSticky() {
        Standing base = new Standing(CITIZEN, 10);

        assertAll(
            () -> assertSame(CITIZEN, base.withDelta(-10).citizen()),
            () -> assertSame(CITIZEN, base.withValue(Integer.MIN_VALUE).citizen())
        );
    }

    @Test
    @DisplayName("repeating withDelta(0) 100 times is value-stable — no drift, no aliasing surprises")
    void repeatedZeroDeltaIsStable() {
        Standing base = new Standing(CITIZEN, 7);

        Standing current = base;
        for (int i = 0; i < 100; i++) {
            current = current.withDelta(0);
        }

        assertEquals(base, current,
            "100 zero-deltas later the standing is unchanged");
    }
}
