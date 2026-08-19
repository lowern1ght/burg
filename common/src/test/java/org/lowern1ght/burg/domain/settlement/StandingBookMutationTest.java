package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link StandingBook}: mutations are
 * idempotent at fixed points, opposing adjustments cancel back to EMPTY,
 * and the sparse-roll contract (zero entries never persist) holds from
 * every direction. Written to kill mutants like a {@code set} that
 * appends instead of replaces, or a drop-check that fires on negative
 * instead of zero.
 */
class StandingBookMutationTest {

    private static CitizenId citizen(String tail) {
        return CitizenId.of(UUID.nameUUIDFromBytes(tail.getBytes()));
    }

    private static final CitizenId ALICE = citizen("alice");
    private static final CitizenId BOB = citizen("bob");

    @Test
    @DisplayName("EMPTY reads zero for any citizen — with the QUERIED citizen, not the ZERO constant")
    void emptyBookReadsQueriedCitizen() {
        Standing read = StandingBook.EMPTY.standingFor(ALICE);

        assertAll(
            () -> assertTrue(StandingBook.EMPTY.isEmpty()),
            () -> assertEquals(0, StandingBook.EMPTY.size()),
            () -> assertEquals(0, read.value(), "an unknown citizen reads as zero"),
            () -> assertEquals(ALICE, read.citizen(),
                "the returned standing carries the queried citizen (kills a mutant returning ZERO)")
        );
    }

    @Test
    @DisplayName("set to the same value twice is idempotent — one entry, same score")
    void setTwiceIsIdempotent() {
        StandingBook once = StandingBook.EMPTY.set(ALICE, 5);
        StandingBook twice = once.set(ALICE, 5);

        assertAll(
            () -> assertEquals(once.size(), twice.size(),
                "re-setting the same value must not append a second entry"),
            () -> assertEquals(5, twice.standingFor(ALICE).value())
        );
    }

    @Test
    @DisplayName("set to zero drops the entry and an all-zero book collapses to EMPTY")
    void setToZeroDrops() {
        StandingBook book = StandingBook.EMPTY.set(ALICE, 5).set(BOB, 3);

        StandingBook withoutAlice = book.set(ALICE, 0);
        StandingBook withoutBoth = withoutAlice.set(BOB, 0);

        assertAll(
            () -> assertEquals(1, withoutAlice.size()),
            () -> assertEquals(0, withoutBoth.size()),
            () -> assertSame(StandingBook.EMPTY, withoutBoth,
                "a book drained to zero entries collapses to the EMPTY sentinel"),
            () -> assertEquals(0, withoutBoth.standingFor(ALICE).value(),
                "the dropped citizen reads as zero again")
        );
    }

    @Test
    @DisplayName("set(alice, 0) on an EMPTY book is the EMPTY book — same instance")
    void setZeroOnEmptyIsEmpty() {
        assertSame(StandingBook.EMPTY, StandingBook.EMPTY.set(ALICE, 0),
            "setting an absent citizen to zero is a no-op at the sentinel");
    }

    @Test
    @DisplayName("adjust by zero changes nothing — value-wise identical book")
    void adjustZeroIsNoOp() {
        StandingBook book = StandingBook.EMPTY.set(ALICE, 5).set(BOB, 3);
        StandingBook nudged = book.adjust(ALICE, 0);

        assertEquals(book.size(), nudged.size(), "no entry appears");
        assertEquals(5, nudged.standingFor(ALICE).value(), "no score moves");

        assertSame(StandingBook.EMPTY, StandingBook.EMPTY.adjust(ALICE, 0),
            "adjust(0) on the EMPTY book collapses back to the sentinel");
    }

    @Test
    @DisplayName("adjust(+5).adjust(-5) cancels to the original book")
    void adjustCancels() {
        StandingBook book = StandingBook.EMPTY.set(ALICE, 5).set(BOB, 3);
        StandingBook roundTripped = book.adjust(ALICE, 5).adjust(ALICE, -5);

        assertAll(
            () -> assertEquals(book.size(), roundTripped.size()),
            () -> assertEquals(5, roundTripped.standingFor(ALICE).value()),
            () -> assertEquals(3, roundTripped.standingFor(BOB).value(),
                "other citizens are untouched by the round trip")
        );
    }

    @Test
    @DisplayName("adjust accumulates: two +5 adjustments equal one +10")
    void adjustAccumulates() {
        StandingBook byTwo = StandingBook.EMPTY.adjust(ALICE, 5).adjust(ALICE, 5);
        StandingBook byOne = StandingBook.EMPTY.adjust(ALICE, 10);

        assertAll(
            () -> assertEquals(byOne.size(), byTwo.size()),
            () -> assertEquals(byOne.standingFor(ALICE).value(), byTwo.standingFor(ALICE).value()),
            () -> assertEquals(10, byTwo.standingFor(ALICE).value())
        );
    }

    @Test
    @DisplayName("adjust that lands exactly on zero drops the entry")
    void adjustToZeroDrops() {
        StandingBook book = StandingBook.EMPTY.set(ALICE, 5).set(BOB, 3);
        StandingBook cancelled = book.adjust(ALICE, -5);

        assertAll(
            () -> assertEquals(1, cancelled.size(), "alice dropped, bob kept"),
            () -> assertEquals(0, cancelled.standingFor(ALICE).value()),
            () -> assertEquals(3, cancelled.standingFor(BOB).value())
        );
    }

    @Test
    @DisplayName("adjust into negative territory keeps the entry — negative standing persists")
    void adjustNegativeKeeps() {
        StandingBook book = StandingBook.EMPTY.set(ALICE, 2).adjust(ALICE, -7);

        assertAll(
            () -> assertEquals(1, book.size(),
                "a negative score is not zero — the entry stays on the roll"),
            () -> assertEquals(-5, book.standingFor(ALICE).value())
        );
    }

    @Test
    @DisplayName("of() drops zero entries from the source and an all-zero source is EMPTY")
    void ofDropsZeroEntries() {
        Map<CitizenId, Standing> source = new LinkedHashMap<>();
        source.put(ALICE, new Standing(ALICE, 0));
        source.put(BOB, new Standing(BOB, 4));

        StandingBook book = StandingBook.of(source);

        assertAll(
            () -> assertEquals(1, book.size(), "the zero entry is dropped at construction"),
            () -> assertEquals(4, book.standingFor(BOB).value()),
            () -> assertEquals(0, book.standingFor(ALICE).value(),
                "the dropped citizen reads as zero")
        );

        Map<CitizenId, Standing> allZero = new LinkedHashMap<>();
        allZero.put(ALICE, new Standing(ALICE, 0));
        assertSame(StandingBook.EMPTY, StandingBook.of(allZero),
            "an all-zero source collapses to the EMPTY sentinel");
        assertSame(StandingBook.EMPTY, StandingBook.of(Map.of()),
            "an empty source is the EMPTY sentinel");
    }

    @Test
    @DisplayName("of() defensively copies — mutating the source after construction does not leak")
    void ofDefensivelyCopies() {
        Map<CitizenId, Standing> source = new LinkedHashMap<>();
        source.put(ALICE, new Standing(ALICE, 5));

        StandingBook book = StandingBook.of(source);
        source.put(BOB, new Standing(BOB, 9));
        source.remove(ALICE);

        assertAll(
            () -> assertEquals(1, book.size(), "post-construction writes to the source do not leak in"),
            () -> assertEquals(5, book.standingFor(ALICE).value()),
            () -> assertEquals(0, book.standingFor(BOB).value())
        );
    }

    @Test
    @DisplayName("entries() is a read-only view")
    void entriesAreReadOnly() {
        StandingBook book = StandingBook.EMPTY.set(ALICE, 5);

        assertThrows(UnsupportedOperationException.class,
            () -> book.entries().put(BOB, new Standing(BOB, 1)),
            "the roll cannot be mutated through the view");
    }

    @Test
    @DisplayName("mutators never touch the receiver — the original book survives every operation")
    void immutabilityOfReceiver() {
        StandingBook book = StandingBook.EMPTY.set(ALICE, 5);

        StandingBook afterSet = book.set(ALICE, 9);
        StandingBook afterAdjust = book.adjust(ALICE, 3);
        StandingBook afterDrop = book.set(ALICE, 0);

        assertAll(
            () -> assertEquals(5, book.standingFor(ALICE).value(),
                "the receiver is unchanged by set"),
            () -> assertEquals(5, book.standingFor(ALICE).value(),
                "the receiver is unchanged by adjust"),
            () -> assertEquals(1, book.size(),
                "the receiver keeps its entry after another book dropped it"),
            () -> assertEquals(9, afterSet.standingFor(ALICE).value()),
            () -> assertEquals(8, afterAdjust.standingFor(ALICE).value()),
            () -> assertEquals(0, afterDrop.size())
        );
    }

    @Test
    @DisplayName("standingFor on a book with entries: known citizens read their score, unknown read zero")
    void standingForKnownAndUnknown() {
        StandingBook book = StandingBook.EMPTY.set(ALICE, 5);

        assertAll(
            () -> assertEquals(5, book.standingFor(ALICE).value()),
            () -> assertEquals(0, book.standingFor(BOB).value(),
                "a citizen not on the roll reads as zero, never as absent"),
            () -> assertEquals(BOB, book.standingFor(BOB).citizen())
        );
    }

    @Test
    @DisplayName("empty() is the EMPTY sentinel — one object, referentially stable")
    void emptyFactoryIsSentinel() {
        assertSame(StandingBook.EMPTY, StandingBook.empty());
        assertNotEquals(null, StandingBook.EMPTY);
        assertTrue(StandingBook.empty().isEmpty());
    }
}
