package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester edge cases for {@link StandingBook}: the sparse-roll boundary the
 * existing tests do not spell out — ONLY exact zero drops, negative scores
 * persist — plus null guards, score extremes, the rebuild-from-entries
 * round-trip, and repeat-mutation stability.
 */
class StandingBookEdgeCasesTest {

    private static CitizenId id(long seed) {
        return CitizenId.of(new UUID(0x1234L, seed));
    }

    @Test
    @DisplayName("ONLY exact zero drops — a negative score persists on the roll")
    void negativeScoresPersist() {
        StandingBook book = StandingBook.EMPTY.adjust(id(1), -5);

        assertAll(
            () -> assertEquals(1, book.size(),
                "-5 is not DEFAULT, so the entry stays"),
            () -> assertEquals(-5, book.standingFor(id(1)).value(),
                "a fresh citizen adjusted below zero reads their negative score"),
            () -> assertTrue(!book.isEmpty())
        );
    }

    @Test
    @DisplayName("adjust(id, 0) on a citizen not on the roll leaves the book EMPTY")
    void zeroDeltaOnMissingCitizenIsNoOp() {
        StandingBook book = StandingBook.EMPTY.adjust(id(9), 0);

        assertAll(
            () -> assertSame(StandingBook.EMPTY, book,
                "0 + 0 == DEFAULT drops the would-be entry — the book stays the sentinel"),
            () -> assertEquals(0, book.size()),
            () -> assertEquals(Standing.DEFAULT, book.standingFor(id(9)).value())
        );
    }

    @Test
    @DisplayName("adjust(id, 0) on a citizen already on the roll keeps them there")
    void zeroDeltaOnExistingCitizenKeepsEntry() {
        StandingBook book = StandingBook.EMPTY.set(id(9), 4).adjust(id(9), 0);

        assertAll(
            () -> assertEquals(1, book.size(),
                "the score 4+0 is non-zero, so the entry survives"),
            () -> assertEquals(4, book.standingFor(id(9)).value())
        );
    }

    @Test
    @DisplayName("crossing zero downward keeps the citizen on the roll with a negative score")
    void crossingZeroDownwardKeepsEntry() {
        StandingBook book = StandingBook.EMPTY.set(id(9), 3).adjust(id(9), -5);

        assertAll(
            () -> assertEquals(1, book.size(),
                "3 - 5 = -2 ≠ DEFAULT — the entry stays"),
            () -> assertEquals(-2, book.standingFor(id(9)).value())
        );
    }

    @Test
    @DisplayName("extreme scores are persisted, not clamped")
    void extremeScores() {
        StandingBook maxed = StandingBook.EMPTY.set(id(1), Integer.MAX_VALUE);
        StandingBook mined = StandingBook.EMPTY.set(id(2), Integer.MIN_VALUE);

        assertAll(
            () -> assertEquals(Integer.MAX_VALUE, maxed.standingFor(id(1)).value()),
            () -> assertEquals(Integer.MIN_VALUE, mined.standingFor(id(2)).value())
        );
    }

    @Test
    @DisplayName("null citizens are rejected by every public method that takes one")
    void nullBoundaries() {
        assertAll(
            () -> assertThrows(NullPointerException.class,
                () -> StandingBook.EMPTY.standingFor(null)),
            () -> assertThrows(NullPointerException.class,
                () -> StandingBook.EMPTY.set(null, 5)),
            () -> assertThrows(NullPointerException.class,
                () -> StandingBook.EMPTY.adjust(null, 5)),
            () -> assertThrows(NullPointerException.class,
                () -> StandingBook.of(null))
        );
    }

    @Test
    @DisplayName("standingFor names the ASKED citizen — a miss is their zero, not the sentinel's")
    void missReturnsTheAskedCitizensZero() {
        Standing miss = StandingBook.EMPTY.standingFor(id(7));

        assertAll(
            () -> assertEquals(id(7), miss.citizen(),
                "the returned standing carries the asked citizen, not CitizenId.EMPTY"),
            () -> assertNotSame(Standing.ZERO, miss,
                "a miss is NOT the ZERO sentinel — same score, different citizen")
        );
    }

    @Test
    @DisplayName("of(entries-as-map) rebuilds the same book — the cache-rebuild semantics")
    void rebuildFromEntriesIsValueStable() {
        // The Town facade hands out snapshots; the future rebuild path reads
        // entries() and feeds a map back to of(). Pin value stability.
        StandingBook original = StandingBook.EMPTY
            .set(id(1), 10)
            .set(id(2), -3)
            .set(id(3), 7);

        Map<CitizenId, Standing> source = new LinkedHashMap<>();
        original.entries().forEach(source::put);
        StandingBook rebuilt = StandingBook.of(source);

        assertAll(
            () -> assertNotSame(original, rebuilt),
            () -> assertEquals(original.size(), rebuilt.size()),
            () -> assertEquals(original.standingFor(id(1)).value(), rebuilt.standingFor(id(1)).value()),
            () -> assertEquals(original.standingFor(id(2)).value(), rebuilt.standingFor(id(2)).value(),
                "the negative score survives the rebuild"),
            () -> assertEquals(original.standingFor(id(3)).value(), rebuilt.standingFor(id(3)).value())
        );
    }

    @Test
    @DisplayName("a rebuilt book that only held a zero-score citizen collapses to EMPTY")
    void rebuildDropsZeroScores() {
        Map<CitizenId, Standing> source = new LinkedHashMap<>();
        source.put(id(1), new Standing(id(1), 0));

        assertSame(StandingBook.EMPTY, StandingBook.of(source),
            "of() is the last line of defence against sparse-book drift");
    }

    @Test
    @DisplayName("two citizens' scores are independent — adjusting one never touches the other")
    void citizensAreIndependent() {
        StandingBook book = StandingBook.EMPTY
            .set(id(1), 10)
            .set(id(2), 20);

        StandingBook adjusted = book.adjust(id(1), -100);

        assertAll(
            () -> assertEquals(-90, adjusted.standingFor(id(1)).value()),
            () -> assertEquals(20, adjusted.standingFor(id(2)).value(),
                "the bystander is untouched"),
            () -> assertEquals(10, book.standingFor(id(1)).value(),
                "the original book is unchanged (immutability)")
        );
    }

    @Test
    @DisplayName("entries() view is unmodifiable")
    void entriesViewIsUnmodifiable() {
        StandingBook book = StandingBook.EMPTY.set(id(1), 10);

        assertThrows(UnsupportedOperationException.class,
            () -> book.entries().put(id(2), new Standing(id(2), 5)));
    }

    @Test
    @DisplayName("repeating the same adjust 100 times from one source yields identical results")
    void repeatedAdjustIsStable() {
        StandingBook source = StandingBook.EMPTY.set(id(1), 10);
        StandingBook first = null;

        for (int i = 0; i < 100; i++) {
            StandingBook result = source.adjust(id(2), 5);
            if (first == null) {
                first = result;
            } else {
                assertEquals(first.size(), result.size(), "iteration " + i);
                assertEquals(first.standingFor(id(2)).value(), result.standingFor(id(2)).value(),
                    "iteration " + i);
            }
        }

        assertEquals(10, source.standingFor(id(1)).value(),
            "the source is unchanged after 100 mutations of it");
    }
}
