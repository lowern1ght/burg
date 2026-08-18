package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The standing book, in pure JUnit. The book is immutable — every mutator
 * returns a new book — and a citizen not on the roll reads as zero. The
 * default-empty path is the additive NBT default for old saves.
 *
 * <p>Two correctness traps the unit tests are explicitly here to catch:
 * (1) a citizen dropped to zero must be removed from the roll so the
 * persisted NBT stays sparse, and (2) the empty-book sentinel must remain
 * referentially stable so equality checks elsewhere are cheap.
 */
class StandingBookTest {

    private static CitizenId id(String raw) {
        return CitizenId.parse(raw);
    }

    private static CitizenId id(int seed) {
        // Deterministic UUIDs so the assertions stay legible.
        return CitizenId.of(new UUID(0x1234L, seed));
    }

    @Test
    @DisplayName("the additive default for old saves is an empty book")
    void emptyIsTheDefault() {
        StandingBook book = StandingBook.empty();
        assertAll(
            () -> assertSame(StandingBook.EMPTY, book, "empty() returns the EMPTY sentinel"),
            () -> assertTrue(book.isEmpty()),
            () -> assertEquals(0, book.size()),
            () -> assertEquals(Standing.DEFAULT, book.standingFor(id(1)).value(),
                "a citizen not on the roll reads as zero")
        );
    }

    @Test
    @DisplayName("set replaces the score and zero entries drop out of the roll")
    void setAndDrop() {
        StandingBook book = StandingBook.empty()
            .set(id(1), 10)
            .set(id(2), 20);

        assertAll(
            () -> assertEquals(2, book.size()),
            () -> assertEquals(10, book.standingFor(id(1)).value()),
            () -> assertEquals(20, book.standingFor(id(2)).value())
        );

        StandingBook dropped = book.set(id(1), Standing.DEFAULT);
        assertAll(
            () -> assertEquals(1, dropped.size(),
                "setting a score to zero removes the citizen from the roll"),
            () -> assertEquals(20, dropped.standingFor(id(2)).value(),
                "the surviving entry is untouched"),
            () -> assertEquals(Standing.DEFAULT, dropped.standingFor(id(1)).value(),
                "the dropped citizen reads as zero (not absent)")
        );
    }

    @Test
    @DisplayName("adjust adds to the running total")
    void adjustAccumulates() {
        StandingBook book = StandingBook.empty().adjust(id(1), 5);
        assertEquals(5, book.standingFor(id(1)).value());

        StandingBook grown = book.adjust(id(1), 7);
        assertEquals(12, grown.standingFor(id(1)).value());

        StandingBook newCitizen = grown.adjust(id(2), 3);
        assertAll(
            () -> assertEquals(12, newCitizen.standingFor(id(1)).value(),
                "the original citizen is unchanged"),
            () -> assertEquals(3, newCitizen.standingFor(id(2)).value(),
                "a fresh citizen gets a fresh entry")
        );
    }

    @Test
    @DisplayName("adjust can drive a score back to zero and drops the entry")
    void adjustCanClear() {
        StandingBook book = StandingBook.empty()
            .set(id(1), 10)
            .adjust(id(1), -10);

        assertAll(
            () -> assertTrue(book.isEmpty(),
                "a book emptied by adjustment collapses to EMPTY on the next read"),
            () -> assertSame(StandingBook.EMPTY, book,
                "an emptied book is the EMPTY sentinel"),
            () -> assertEquals(Standing.DEFAULT, book.standingFor(id(1)).value())
        );
    }

    @Test
    @DisplayName("of() builds a book from a map and drops zero entries defensively")
    void ofFromMap() {
        Map<CitizenId, Standing> source = Map.of(
            id(1), new Standing(id(1), 10),
            id(2), new Standing(id(2), 0),
            id(3), new Standing(id(3), -5)
        );
        StandingBook book = StandingBook.of(source);

        assertAll(
            () -> assertEquals(2, book.size(),
                "the zero entry is dropped at construction time"),
            () -> assertEquals(10, book.standingFor(id(1)).value()),
            () -> assertEquals(Standing.DEFAULT, book.standingFor(id(2)).value(),
                "the dropped citizen reads as zero"),
            () -> assertEquals(-5, book.standingFor(id(3)).value(),
                "negative scores are preserved (the model has no lower bound)")
        );
    }

    @Test
    @DisplayName("each mutator returns a new book — the input is not mutated")
    void immutability() {
        StandingBook before = StandingBook.empty().set(id(1), 10);
        StandingBook after = before.adjust(id(1), 5);

        assertAll(
            () -> assertEquals(10, before.standingFor(id(1)).value(),
                "the original book is unchanged"),
            () -> assertEquals(15, after.standingFor(id(1)).value(),
                "the new book reflects the mutation"),
            () -> assertFalse(before == after, "two distinct instances")
        );
    }
}