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
 * Mutation-style invariants for {@link CitizenId}: the canonical-form
 * contract (lowercase UUID string), boundary round trips through
 * {@code UUID}, and the lenient edge. Kills mutants like a {@code parse}
 * that stores the raw input (breaking case normalization) or a
 * {@code parseOrEmpty} that throws on garbage.
 */
class CitizenIdMutationTest {

    @Test
    @DisplayName("of(UUID) stores the canonical lowercase form and toUuid round-trips")
    void ofUuidRoundTrip() {
        UUID uuid = UUID.randomUUID();
        CitizenId citizen = CitizenId.of(uuid);

        assertAll(
            () -> assertEquals(uuid.toString(), citizen.value(),
                "the canonical form is exactly UUID.toString()"),
            () -> assertEquals(uuid, citizen.toUuid(),
                "toUuid reproduces the original UUID")
        );
    }

    @Test
    @DisplayName("parse canonicalizes case — an uppercase UUID string loads as the lowercase form")
    void parseCanonicalizesCase() {
        UUID uuid = UUID.randomUUID();
        String upper = uuid.toString().toUpperCase(java.util.Locale.ROOT);

        CitizenId parsed = CitizenId.parse(upper);

        assertAll(
            () -> assertEquals(uuid.toString(), parsed.value(),
                "parse stores the canonical lowercase form, not the raw input"),
            () -> assertEquals(uuid, parsed.toUuid())
        );
    }

    @Test
    @DisplayName("parse rejects malformed strings fast")
    void parseRejectsGarbage() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> CitizenId.parse("not-a-uuid")),
            () -> assertThrows(IllegalArgumentException.class, () -> CitizenId.parse("123")),
            () -> assertThrows(NullPointerException.class, () -> CitizenId.parse(null))
        );
    }

    @Test
    @DisplayName("parseOrEmpty maps null / empty / garbage onto the EMPTY sentinel")
    void parseOrEmptyLenient() {
        assertAll(
            () -> assertSame(CitizenId.EMPTY, CitizenId.parseOrEmpty(null)),
            () -> assertSame(CitizenId.EMPTY, CitizenId.parseOrEmpty("")),
            () -> assertSame(CitizenId.EMPTY, CitizenId.parseOrEmpty("garbage")),
            () -> assertEquals(CitizenId.of(UUID.fromString(CitizenId.EMPTY.value())),
                CitizenId.parseOrEmpty(CitizenId.EMPTY.value()),
                "a well-formed string parses normally through the lenient edge")
        );
    }

    @Test
    @DisplayName("the EMPTY sentinel is the all-zero UUID and still round-trips through toUuid")
    void emptySentinelRoundTrips() {
        assertAll(
            () -> assertEquals("00000000-0000-0000-0000-000000000000", CitizenId.EMPTY.value()),
            () -> assertEquals(new UUID(0L, 0L), CitizenId.EMPTY.toUuid(),
                "the sentinel is a real UUID — the boundary conversion never throws"),
            () -> assertEquals(CitizenId.EMPTY, CitizenId.of(new UUID(0L, 0L)))
        );
    }

    @Test
    @DisplayName("equality is the canonical string — two random UUIDs are different citizens")
    void equalityOnCanonicalForm() {
        CitizenId a = CitizenId.of(UUID.randomUUID());
        CitizenId b = CitizenId.of(UUID.randomUUID());

        assertAll(
            () -> assertEquals(a, CitizenId.parse(a.value())),
            () -> assertEquals(a, CitizenId.parse(a.value().toUpperCase(java.util.Locale.ROOT)),
                "case differences normalize away before equality"),
            () -> assertNotEquals(a, b)
        );
    }

    @Test
    @DisplayName("a null value is rejected at construction")
    void nullValueRejected() {
        assertThrows(NullPointerException.class, () -> new CitizenId(null));
        assertThrows(NullPointerException.class, () -> CitizenId.of(null));
    }
}
