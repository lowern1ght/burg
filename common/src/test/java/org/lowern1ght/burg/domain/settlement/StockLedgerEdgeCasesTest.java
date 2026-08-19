package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester edge cases for {@link StockLedger}. Where {@code StockLedgerTest}
 * pins the happy-path recipe, this file tortures the numeric boundaries
 * (Integer.MAX_VALUE sums), the null boundaries of every mutator, the
 * rebuild-from-entries round-trip the future Town-facade cache discipline
 * will rely on, and the repeat-a-mutator stability of the immutable design.
 */
class StockLedgerEdgeCasesTest {

    private static final ItemId STONE = ItemId.of("minecraft:stone");
    private static final ItemId OAK_LOG = ItemId.of("minecraft:oak_log");
    private static final ItemId DIRT = ItemId.of("minecraft:dirt");

    @Test
    @DisplayName("DOCUMENTED LATENT BUG: add() past Integer.MAX_VALUE wraps to a negative entry")
    void documentedLatentBug_addOverflowWrapsNegative() {
        // The class javadoc promises "a map from ItemId to a NON-NEGATIVE
        // quantity". add() computes current + quantity in int arithmetic
        // with no overflow guard, so a second large deposit wraps below
        // zero and the ledger then serves a NEGATIVE quantity — breaking
        // its own invariant and the take() precondition silently
        // (take(item, 1) now succeeds against "negative stock").
        //
        // This test is a characterization of the CURRENT wrap-around so the
        // bug is executable. When this is fixed (Math.addExact + Arithmetic-
        // Exception, or saturating at MAX_VALUE), update this test to pin
        // the fix — the failure of this test IS the bug report.
        StockLedger maxed = StockLedger.EMPTY.add(STONE, Integer.MAX_VALUE);

        StockLedger overflowed = maxed.add(STONE, Integer.MAX_VALUE);

        assertTrue(overflowed.get(STONE) < 0,
            "LATENT BUG pinned: MAX + MAX wraps to " + overflowed.get(STONE)
                + " — the ledger now holds a negative quantity");
    }

    @Test
    @DisplayName("a single deposit of Integer.MAX_VALUE is held exactly — no premature saturation")
    void singleMaxDepositIsExact() {
        StockLedger maxed = StockLedger.EMPTY.add(STONE, Integer.MAX_VALUE);

        assertAll(
            () -> assertEquals(Integer.MAX_VALUE, maxed.get(STONE)),
            () -> assertEquals(1, maxed.size())
        );
    }

    @Test
    @DisplayName("add() of zero is a no-op returning the SAME instance, not a copy")
    void addZeroReturnsSameInstance() {
        StockLedger before = StockLedger.EMPTY.add(STONE, 5);

        assertSame(before, before.add(STONE, 0),
            "a zero deposit must not allocate — assertSame pins the fast path");
        assertSame(before, before.add(OAK_LOG, 0),
            "zero of a NEW item is also a no-op — no empty entry appears");
        assertEquals(0, before.get(OAK_LOG));
    }

    @Test
    @DisplayName("null item is rejected by every public mutator and reader")
    void nullItemBoundaries() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 5);

        assertAll(
            () -> assertThrows(NullPointerException.class, () -> ledger.get(null)),
            () -> assertThrows(NullPointerException.class, () -> ledger.add(null, 1)),
            () -> assertThrows(NullPointerException.class, () -> ledger.take(null, 1)),
            () -> assertThrows(NullPointerException.class, () -> ledger.merge(null)),
            () -> assertThrows(NullPointerException.class, () -> StockLedger.of(null))
        );
    }

    @Test
    @DisplayName("of() silently drops null-valued entries — the last line of defence is quiet")
    void ofDropsNullValues() {
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(STONE, 10);
        source.put(OAK_LOG, null);

        StockLedger ledger = StockLedger.of(source);

        assertAll(
            () -> assertEquals(1, ledger.size(),
                "the null-valued entry is dropped, not defaulted"),
            () -> assertEquals(10, ledger.get(STONE)),
            () -> assertEquals(0, ledger.get(OAK_LOG))
        );
    }

    @Test
    @DisplayName("of() rejects a null key mid-map — the failure is loud, not a skip")
    void ofRejectsNullKeys() {
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(STONE, 10);
        source.put(null, 5);

        assertThrows(NullPointerException.class, () -> StockLedger.of(source),
            "a null key is a hard failure, unlike a null value");
    }

    @Test
    @DisplayName("of(entries()) rebuilds the same ledger — the Town-facade cache-rebuild semantics")
    void rebuildFromEntriesIsValueStable() {
        // The future carve wires Town.stockLedger()/applyStockLedger through
        // a rebuild: read entries, hand them back to of(). Pin that the
        // rebuild is value-identical (same keys, same quantities, same
        // order) even though it may be a different instance.
        StockLedger original = StockLedger.EMPTY
            .add(STONE, 10)
            .add(OAK_LOG, 3)
            .take(OAK_LOG, 1);

        StockLedger rebuilt = StockLedger.of(original.entries());

        assertAll(
            () -> assertNotSame(original, rebuilt,
                "the rebuild may be a different instance"),
            () -> assertEquals(original.size(), rebuilt.size()),
            () -> assertEquals(original.get(STONE), rebuilt.get(STONE)),
            () -> assertEquals(original.get(OAK_LOG), rebuilt.get(OAK_LOG)),
            () -> assertEquals(
                original.entries().keySet().stream().toList(),
                rebuilt.entries().keySet().stream().toList(),
                "insertion order survives the rebuild — the apply path stays deterministic")
        );
    }

    @Test
    @DisplayName("re-adding the same item after a full drain lands on the rebuilt ledger")
    void reAddAfterDrainOnRebuild() {
        StockLedger drained = StockLedger.EMPTY
            .add(STONE, 10)
            .take(STONE, 10);

        assertSame(StockLedger.EMPTY, drained,
            "full drain collapses to the sentinel");

        StockLedger rebuilt = StockLedger.of(drained.entries());
        StockLedger reAdded = rebuilt.add(STONE, 7);

        assertAll(
            () -> assertSame(StockLedger.EMPTY, rebuilt,
                "rebuilding an empty entries-view reuses the sentinel"),
            () -> assertEquals(7, reAdded.get(STONE))
        );
    }

    @Test
    @DisplayName("merging a ledger with itself doubles every entry")
    void mergeSelfDoubles() {
        StockLedger ledger = StockLedger.EMPTY
            .add(STONE, 10)
            .add(OAK_LOG, 3);

        StockLedger doubled = ledger.merge(ledger);

        assertAll(
            () -> assertEquals(20, doubled.get(STONE)),
            () -> assertEquals(6, doubled.get(OAK_LOG)),
            () -> assertEquals(2, doubled.size()),
            () -> assertEquals(10, ledger.get(STONE),
                "the receiver is unchanged (immutability)")
        );
    }

    @Test
    @DisplayName("merge with exactly-canceling quantities drops the entry — negative sums are unreachable by merge")
    void mergeExactCancel() {
        // A canceling (sum == 0) merge needs a negative right-hand entry,
        // which is unreachable by construction: of() drops negatives and
        // add() rejects them. The only "canceling" shape a caller can build
        // is a zero-qty source — which of() collapses to EMPTY, making the
        // merge a no-op. Pin both halves of that contract.
        Map<ItemId, Integer> rightSource = new LinkedHashMap<>();
        rightSource.put(STONE, 0);
        StockLedger right = StockLedger.of(rightSource);
        StockLedger left = StockLedger.EMPTY.add(STONE, 5);

        StockLedger merged = left.merge(right);

        assertAll(
            () -> assertTrue(right.isEmpty(),
                "a zero-only source collapses to EMPTY at of()"),
            () -> assertSame(left, merged,
                "merging an effectively-empty ledger returns the receiver unchanged")
        );
    }

    @Test
    @DisplayName("take() of exactly the held amount drops the entry and collapses a single-entry ledger to EMPTY")
    void takeExactAmount() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 5);

        StockLedger emptied = ledger.take(STONE, 5);

        assertAll(
            () -> assertSame(StockLedger.EMPTY, emptied,
                "the last entry dropping collapses to the sentinel"),
            () -> assertEquals(0, emptied.get(STONE)),
            () -> assertEquals(5, ledger.get(STONE),
                "the original is unchanged")
        );
    }

    @Test
    @DisplayName("a failed take leaves the ledger bit-for-bit unchanged — content AND order")
    void failedTakeChangesNothing() {
        StockLedger ledger = StockLedger.EMPTY
            .add(STONE, 5)
            .add(OAK_LOG, 3);

        assertThrows(IllegalStateException.class, () -> ledger.take(STONE, 6));

        StockLedger rebuilt = StockLedger.of(ledger.entries());
        assertAll(
            () -> assertEquals(2, rebuilt.size()),
            () -> assertEquals(5, rebuilt.get(STONE)),
            () -> assertEquals(
                ledger.entries().keySet().stream().toList(),
                rebuilt.entries().keySet().stream().toList(),
                "entry order is untouched by the failed take")
        );
    }

    @Test
    @DisplayName("repeating the same add 100 times from one source yields 100 identical results")
    void repeatedMutatorIsStable() {
        StockLedger source = StockLedger.EMPTY.add(DIRT, 2);
        StockLedger first = null;

        for (int i = 0; i < 100; i++) {
            StockLedger result = source.add(STONE, 5);
            if (first == null) {
                first = result;
            } else {
                assertEquals(first.size(), result.size(), "iteration " + i);
                assertEquals(first.get(STONE), result.get(STONE), "iteration " + i);
            }
        }

        assertAll(
            () -> assertEquals(2, source.get(DIRT),
                "the source is unchanged after 100 mutations of it"),
            () -> assertEquals(0, source.get(STONE))
        );
    }

    @Test
    @DisplayName("mixed-up take/add argument shapes: take uses (item, qty) — an (qty, item) swap cannot compile")
    void argumentShapesAreTyped() {
        // ItemId and int are distinct types, so the classic swapped-argument
        // bug cannot occur at compile time. What CAN occur is a semantic
        // swap: taking OAK_LOG when STONE was meant. Pin that the exception
        // message names the item actually requested — the tester's only
        // defence against copy-paste swaps in caller code.
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 5);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> ledger.take(OAK_LOG, 1));

        assertTrue(thrown.getMessage().contains(OAK_LOG.value()),
            "the failure names the item that was actually requested ('" + thrown.getMessage() + "')");
    }

    @Test
    @DisplayName("EMPTY is a shared singleton — of(emptyMap) and full drains all reuse it")
    void emptySentinelReuse() {
        Map<ItemId, Integer> empty = new LinkedHashMap<>();

        assertAll(
            () -> assertSame(StockLedger.EMPTY, StockLedger.of(empty)),
            () -> assertSame(StockLedger.EMPTY, StockLedger.EMPTY.merge(StockLedger.EMPTY)),
            () -> assertNull(StockLedger.EMPTY.entries().get(STONE),
                "EMPTY's view has no mapping for any item")
        );
    }
}
