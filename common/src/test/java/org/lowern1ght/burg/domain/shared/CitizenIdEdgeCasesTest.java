package org.lowern1ght.burg.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tester edge cases for {@link CitizenId}: boundary UUIDs, non-canonical
 * string casing, the EMPTY sentinel's own round-trip, and the deliberately
 * validation-free canonical constructor (the trust boundary the Town
 * facade leans on).
 */
class CitizenIdEdgeCasesTest {

    @Test
    @DisplayName("of/parse reject null before anything else")
    void nullBoundaries() {
        assertAll(
            () -> assertThrows(NullPointerException.class, () -> CitizenId.of(null)),
            () -> assertThrows(NullPointerException.class, () -> CitizenId.parse(null))
        );
    }

    @Test
    @DisplayName("parse accepts uppercase hex and re-canonicalises to lowercase — one canonical form per UUID")
    void parseCanonicalisesUppercase() {
        String upper = "01234567-89AB-CDEF-0123-456789ABCDEF";
        String lower = upper.toLowerCase(java.util.Locale.ROOT);

        CitizenId fromUpper = CitizenId.parse(upper);
        CitizenId fromLower = CitizenId.parse(lower);

        assertAll(
            () -> assertEquals(lower, fromUpper.value(),
                "the wrapped form is re-canonicalised to lowercase"),
            () -> assertEquals(fromLower, fromUpper,
                "uppercase and lowercase parses of the same UUID are equal"),
            () -> assertEquals(fromLower.hashCode(), fromUpper.hashCode(),
                "hashCode agrees with equals across casings")
        );
    }

    @Test
    @DisplayName("boundary UUIDs round-trip: all-zero, all-f, and the two long extremes")
    void boundaryUuidsRoundTrip() {
        UUID zero = new UUID(0L, 0L);
        UUID allF = new UUID(-1L, -1L);
        UUID minMin = new UUID(Long.MIN_VALUE, Long.MIN_VALUE);
        UUID maxMax = new UUID(Long.MAX_VALUE, Long.MAX_VALUE);

        assertAll(
            () -> assertEquals(zero, CitizenId.of(zero).toUuid()),
            () -> assertEquals(allF, CitizenId.of(allF).toUuid()),
            () -> assertEquals(minMin, CitizenId.of(minMin).toUuid()),
            () -> assertEquals(maxMax, CitizenId.of(maxMax).toUuid())
        );
    }

    @Test
    @DisplayName("EMPTY is the all-zero UUID and survives its own round-trip")
    void emptySentinelRoundTrip() {
        assertAll(
            () -> assertEquals(new UUID(0L, 0L), CitizenId.EMPTY.toUuid(),
                "EMPTY is the all-zero UUID"),
            () -> assertEquals(CitizenId.EMPTY, CitizenId.of(new UUID(0L, 0L)),
                "rebuilding the all-zero UUID yields the same value as EMPTY"),
            () -> assertSame(CitizenId.EMPTY, CitizenId.parseOrEmpty("garbage"),
                "lenient failures reuse the sentinel instance"),
            () -> assertEquals(CitizenId.EMPTY, CitizenId.parse(CitizenId.EMPTY.value()),
                "parsing the sentinel's own string yields an equal — not same — instance"),
            () -> assertEquals(CitizenId.EMPTY.value().toLowerCase(java.util.Locale.ROOT),
                CitizenId.EMPTY.value(),
                "the sentinel form is already canonical lowercase")
        );
    }

    @Test
    @DisplayName("parse rejects structurally-close-but-wrong strings")
    void parseRejectsNearMisses() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> CitizenId.parse("01234567-89AB-CDEF-0123-456789ABCDEF-"),
                "a trailing sixth segment is rejected"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> CitizenId.parse("01234567+89AB-CDEF-0123-456789ABCDEF"),
                "a dash replaced by a plus is rejected"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> CitizenId.parse("0123456789ABCDEF0123456789ABCDEF"),
                "the bare-hex form is not the canonical dashed form"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> CitizenId.parse("zzzzzzzz-89AB-CDEF-0123-456789ABCDEF"),
                "a non-hex segment is rejected")
        );
    }

    @Test
    @DisplayName("the canonical constructor is deliberately validation-free — toUuid() is where garbage fails")
    void canonicalConstructorTrustBoundary() {
        // Characterisation of the documented design: the ctor does not parse
        // (allocation-free NBT load path); a malformed wrapped string only
        // fails when converted back to a UUID. If the ctor ever starts
        // validating, the NBT load path changed shape.
        CitizenId garbage = new CitizenId("definitely-not-a-uuid");

        assertAll(
            () -> assertEquals("definitely-not-a-uuid", garbage.value(),
                "construction of a malformed value does not throw"),
            () -> assertThrows(IllegalArgumentException.class, garbage::toUuid,
                "the conversion back to UUID is where the garbage fails")
        );
    }

    @Test
    @DisplayName("two citizens wrapping different UUIDs are never equal, even one bit apart")
    void inequality() {
        CitizenId a = CitizenId.of(new UUID(1L, 1L));
        CitizenId b = CitizenId.of(new UUID(1L, 2L));
        CitizenId c = CitizenId.of(new UUID(2L, 1L));

        assertAll(
            () -> assertNotEquals(a, b, "least-significant bit differs ⇒ unequal"),
            () -> assertNotEquals(a, c, "most-significant bit differs ⇒ unequal"),
            () -> assertNotEquals(CitizenId.EMPTY, CitizenId.of(new UUID(0L, 1L)),
                "EMPTY only equals the all-zero UUID")
        );
    }

    @Test
    @DisplayName("repeated of/toUuid round-trips are stable (100x)")
    void roundTripStableAcrossRepeats() {
        UUID raw = new UUID(0xCAFEBABEL, 0xDEADBEEFL);
        CitizenId first = CitizenId.of(raw);

        for (int i = 0; i < 100; i++) {
            CitizenId again = CitizenId.of(first.toUuid());
            assertEquals(first, again,
                "round-trip " + i + " is value-stable");
        }
    }
}
