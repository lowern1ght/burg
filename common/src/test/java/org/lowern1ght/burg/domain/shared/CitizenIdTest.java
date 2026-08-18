package org.lowern1ght.burg.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Canonical-form discipline of {@link CitizenId}. The record wraps the
 * canonical {@code UUID.toString()} string and never stores an alternate
 * form — that is the contract the {@code ChatSubscribers} NBT list and the
 * new {@code Standings} NBT list both rely on.
 */
class CitizenIdTest {

    @Test
    @DisplayName("the canonical form survives a UUID round-trip")
    void canonicalForm() {
        UUID raw = UUID.fromString("01234567-89AB-CDEF-0123-456789ABCDEF");
        CitizenId id = CitizenId.of(raw);

        assertAll(
            () -> assertEquals(raw.toString(), id.value(),
                "the wrapped form is the canonical UUID string"),
            () -> assertEquals(raw, id.toUuid(),
                "toUuid() recovers the original UUID exactly")
        );
    }

    @Test
    @DisplayName("two Citizens wrapping the same UUID are equal")
    void equality() {
        UUID raw = UUID.randomUUID();
        assertEquals(CitizenId.of(raw), CitizenId.of(raw));
    }

    @Test
    @DisplayName("parse throws on a non-UUID string; parseOrEmpty yields EMPTY")
    void parsePolicy() {
        assertAll(
            () -> assertSame(CitizenId.EMPTY, CitizenId.parseOrEmpty(null)),
            () -> assertSame(CitizenId.EMPTY, CitizenId.parseOrEmpty("")),
            () -> assertSame(CitizenId.EMPTY, CitizenId.parseOrEmpty("not-a-uuid")),
            () -> assertThrows(IllegalArgumentException.class,
                () -> CitizenId.parse("not-a-uuid"),
                "parse() is strict — the boundary caller must catch its own garbage")
        );
    }
}