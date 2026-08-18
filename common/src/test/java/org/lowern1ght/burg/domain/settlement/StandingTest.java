package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link Standing} record, in pure JUnit. {@code StandingBookTest}
 * exercises the record through the book (the place a {@code Standing} lives
 * day-to-day); this file pins the standalone record contract — the
 * immutable mutators ({@link Standing#withDelta}, {@link Standing#withValue}),
 * the {@code ZERO} sentinel, and the {@link Standing#isZero} predicate that
 * gates the sparse-book drop.
 */
class StandingTest {

    private static CitizenId id(int seed) {
        // Deterministic UUIDs so the assertions stay legible.
        return CitizenId.of(new UUID(0x1234L, seed));
    }

    @Test
    @DisplayName("the additive default for a brand-new entry is zero")
    void zeroIsTheDefault() {
        assertAll(
            () -> assertEquals(0, Standing.DEFAULT),
            () -> assertEquals(CitizenId.EMPTY, Standing.ZERO.citizen(),
                "ZERO carries the EMPTY citizen — a brand-new entry has no actor yet"),
            () -> assertEquals(0, Standing.ZERO.value()),
            () -> assertTrue(Standing.ZERO.isZero())
        );
    }

    @Test
    @DisplayName("the constructor rejects a null citizen")
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class,
            () -> new Standing(null, 5));
    }

    @Test
    @DisplayName("withDelta adds to the running score without changing the citizen")
    void withDeltaAdds() {
        Standing base = new Standing(id(1), 5);

        Standing added = base.withDelta(3);
        Standing subtracted = base.withDelta(-7);

        assertAll(
            () -> assertEquals(8, added.value(),
                "5 + 3 = 8"),
            () -> assertEquals(-2, subtracted.value(),
                "5 - 7 = -2 (the model has no lower bound)"),
            () -> assertEquals(id(1), added.citizen(),
                "the citizen is carried through the delta"),
            () -> assertEquals(id(1), subtracted.citizen()),
            () -> assertEquals(5, base.value(),
                "the original entry is unchanged (immutability)")
        );
    }

    @Test
    @DisplayName("withDelta can drive a positive score back to zero; the result reads as isZero")
    void withDeltaLandsOnZero() {
        Standing base = new Standing(id(1), 10);
        Standing landed = base.withDelta(-10);

        assertAll(
            () -> assertEquals(0, landed.value()),
            () -> assertTrue(landed.isZero(),
                "isZero mirrors the integer check — the book relies on this to drop entries")
        );
    }

    @Test
    @DisplayName("withValue sets the score to an arbitrary integer; the citizen is preserved")
    void withValueSets() {
        Standing base = new Standing(id(1), 5);

        Standing reset = base.withValue(42);
        Standing negative = base.withValue(-9);
        Standing zero = base.withValue(0);

        assertAll(
            () -> assertEquals(42, reset.value()),
            () -> assertEquals(id(1), reset.citizen()),
            () -> assertEquals(-9, negative.value()),
            () -> assertTrue(zero.isZero(),
                "withValue(0) yields an entry that reads as isZero"),
            () -> assertEquals(5, base.value(),
                "the original entry is unchanged")
        );
    }

    @Test
    @DisplayName("isZero only fires at value == 0 — positive and negative scores are not zero")
    void isZeroEdge() {
        assertAll(
            () -> assertTrue(new Standing(id(1), 0).isZero()),
            () -> assertFalse(new Standing(id(1), 1).isZero(),
                "+1 is not zero"),
            () -> assertFalse(new Standing(id(1), -1).isZero(),
                "-1 is not zero — the score is allowed to be negative"),
            () -> assertSame(Standing.ZERO, Standing.ZERO,
                "the ZERO sentinel is referentially stable")
        );
    }

    @Test
    @DisplayName("two Standings with the same citizen and value are equal")
    void equality() {
        Standing a = new Standing(id(1), 5);
        Standing b = new Standing(id(1), 5);
        Standing sameCitizenDifferentScore = new Standing(id(1), 7);
        Standing differentCitizenSameScore = new Standing(id(2), 5);

        assertAll(
            () -> assertEquals(a, b),
            () -> assertNotEquals(a, sameCitizenDifferentScore,
                "different score — not equal"),
            () -> assertNotEquals(a, differentCitizenSameScore,
                "different citizen — not equal")
        );
    }
}
