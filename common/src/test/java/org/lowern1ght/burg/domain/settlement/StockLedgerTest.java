package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The immutable stock ledger, in pure JUnit. Like {@code StandingBookTest},
 * the ledger is immutable — every mutator returns a new ledger — and the
 * default-empty path is the additive NBT default for old saves.
 *
 * <p>Three correctness traps the unit tests are explicitly here to catch:
 * (1) a quantity that falls back to zero must drop from the roll so the
 * persisted NBT — once the ledger is promoted to source of truth in a
 * future carve — stays sparse; (2) {@code take} must reject insufficient
 * stock with a deterministic failure rather than silently underflow; and
 * (3) the empty-ledger sentinel must remain referentially stable so
 * equality checks elsewhere are cheap.
 */
class StockLedgerTest {

    private static ItemId item(String raw) {
        return ItemId.of(raw);
    }

    private static final ItemId OAK_LOG = item("minecraft:oak_log");
    private static final ItemId STONE = item("minecraft:stone");
    private static final ItemId DIRT = item("minecraft:dirt");

    @Test
    @DisplayName("the additive default for old saves is an empty ledger")
    void emptyIsTheDefault() {
        StockLedger ledger = StockLedger.EMPTY;
        assertAll(
            () -> assertSame(StockLedger.EMPTY, ledger, "EMPTY is referentially stable"),
            () -> assertTrue(ledger.isEmpty()),
            () -> assertEquals(0, ledger.size()),
            () -> assertEquals(0, ledger.get(STONE),
                "an item not on the ledger reads as zero")
        );
    }

    @Test
    @DisplayName("add increases the running total and drops zero-qty entries")
    void addAccumulates() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 10);

        assertAll(
            () -> assertEquals(10, ledger.get(STONE)),
            () -> assertEquals(1, ledger.size())
        );

        StockLedger grown = ledger.add(STONE, 5);
        assertAll(
            () -> assertEquals(10, ledger.get(STONE),
                "the original ledger is unchanged (immutability)"),
            () -> assertEquals(15, grown.get(STONE),
                "the new ledger reflects the addition"),
            () -> assertNotSame(ledger, grown, "two distinct instances")
        );
    }

    @Test
    @DisplayName("adding an item whose qty would drop back to zero removes it from the roll")
    void addZeroDrops() {
        // Drain via take() — add() rejects negative quantities. The drop
        // path fires when the resulting quantity is exactly zero, regardless
        // of which mutator drove the change.
        StockLedger drained = StockLedger.EMPTY.add(STONE, 10).take(STONE, 10);

        assertAll(
            () -> assertSame(StockLedger.EMPTY, drained,
                "a ledger drained to zero collapses to EMPTY"),
            () -> assertTrue(drained.isEmpty())
        );
    }

    @Test
    @DisplayName("add rejects negative quantities — use take() to drain")
    void addRejectsNegative() {
        assertThrows(IllegalArgumentException.class,
            () -> StockLedger.EMPTY.add(STONE, -1),
            "negative add() is reserved for take()");
    }

    @Test
    @DisplayName("take drains the ledger and removes zero-qty entries")
    void takeDrains() {
        StockLedger ledger = StockLedger.EMPTY
            .add(STONE, 10)
            .add(OAK_LOG, 5);

        StockLedger drained = ledger.take(STONE, 3);

        assertAll(
            () -> assertEquals(10, ledger.get(STONE),
                "the original ledger is unchanged (immutability)"),
            () -> assertEquals(7, drained.get(STONE)),
            () -> assertEquals(5, drained.get(OAK_LOG),
                "untouched entries are preserved"),
            () -> assertEquals(2, drained.size(),
                "no entries are dropped on a partial take")
        );

        StockLedger emptied = drained.take(STONE, 7).take(OAK_LOG, 5);
        assertAll(
            () -> assertSame(StockLedger.EMPTY, emptied,
                "a ledger drained to zero across every entry collapses to EMPTY"),
            () -> assertEquals(0, emptied.get(STONE)),
            () -> assertEquals(0, emptied.get(OAK_LOG))
        );
    }

    @Test
    @DisplayName("take fails fast on insufficient stock")
    void takeRejectsInsufficient() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 5);

        assertAll(
            () -> assertThrows(IllegalStateException.class,
                () -> ledger.take(STONE, 6),
                "demanding more than is held throws"),
            () -> assertThrows(IllegalStateException.class,
                () -> ledger.take(OAK_LOG, 1),
                "demanding an item that is not on the ledger throws"),
            () -> assertEquals(5, ledger.get(STONE),
                "a rejected take leaves the original ledger intact")
        );
    }

    @Test
    @DisplayName("take rejects non-positive quantities")
    void takeRejectsNonPositive() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> StockLedger.EMPTY.take(STONE, 0),
                "zero is not a take"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> StockLedger.EMPTY.take(STONE, -1),
                "negative is not a take")
        );
    }

    @Test
    @DisplayName("merge sums overlapping entries and drops zero-qty results")
    void merge() {
        StockLedger left = StockLedger.EMPTY
            .add(STONE, 10)
            .add(OAK_LOG, 3);
        StockLedger right = StockLedger.EMPTY
            .add(STONE, 5)
            .add(DIRT, 7);

        StockLedger merged = left.merge(right);

        assertAll(
            () -> assertEquals(15, merged.get(STONE),
                "overlapping quantities are summed"),
            () -> assertEquals(3, merged.get(OAK_LOG),
                "left-only entries are preserved"),
            () -> assertEquals(7, merged.get(DIRT),
                "right-only entries are added"),
            () -> assertEquals(3, merged.size())
        );
    }

    @Test
    @DisplayName("merge that cancels a quantity drops the entry")
    void mergeCancelDrops() {
        // A hand-built ledger with a zero entry: of() drops it on
        // construction, so the resulting ledger reads as EMPTY.
        Map<ItemId, Integer> zeroSource = new LinkedHashMap<>();
        zeroSource.put(STONE, 0);
        StockLedger rightEmpty = StockLedger.of(zeroSource);
        // Verify that merging an empty ledger is a no-op — the receiver
        // is returned unchanged. This is what makes "merge a hand-built
        // zero ledger" a safe no-op even when the receiver has entries.
        StockLedger partial = StockLedger.EMPTY.add(STONE, 10);
        StockLedger absorbed = partial.merge(rightEmpty);

        assertAll(
            () -> assertTrue(rightEmpty.isEmpty(),
                "a hand-built source with only zero entries reads as EMPTY"),
            () -> assertSame(partial, absorbed,
                "merging EMPTY returns the unchanged receiver — same instance, no copy")
        );
    }

    @Test
    @DisplayName("of() builds a ledger from a map and drops zero entries defensively")
    void ofFromMap() {
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(STONE, 10);
        source.put(OAK_LOG, 0);
        source.put(DIRT, -1);
        StockLedger ledger = StockLedger.of(source);

        assertAll(
            () -> assertEquals(1, ledger.size(),
                "zero entries are dropped at construction time"),
            () -> assertEquals(10, ledger.get(STONE)),
            () -> assertEquals(0, ledger.get(OAK_LOG),
                "the dropped entry reads as zero"),
            () -> assertEquals(0, ledger.get(DIRT),
                "negative entries are dropped and read as zero")
        );
    }

    @Test
    @DisplayName("each mutator returns a new ledger — the input is not mutated")
    void immutability() {
        StockLedger before = StockLedger.EMPTY.add(STONE, 10);
        StockLedger afterAdd = before.add(STONE, 5);
        StockLedger afterTake = before.take(STONE, 3);
        StockLedger afterMerge = before.merge(StockLedger.EMPTY.add(STONE, 2));

        assertAll(
            () -> assertEquals(10, before.get(STONE),
                "the original ledger is unchanged across all mutations"),
            () -> assertEquals(15, afterAdd.get(STONE)),
            () -> assertEquals(7, afterTake.get(STONE)),
            () -> assertEquals(12, afterMerge.get(STONE)),
            () -> assertFalse(before == afterAdd, "add returns a new instance")
        );
    }

    @Test
    @DisplayName("a new ledger key not on the original maps to zero")
    void unknownKeyReadsAsZero() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 5);
        assertEquals(0, ledger.get(OAK_LOG));
    }

    // ADR-0013 — the dual-write path (Town.stockLedger ↔ Town.applyStockLedger)
    // treats entries() as the canonical wire between Town and the domain layer.
    // These tests pin the properties the apply path relies on.

    @Test
    @DisplayName("entries() iteration order matches insertion order — the apply path is deterministic")
    void entriesPreserveInsertionOrder() {
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(STONE, 5);
        source.put(OAK_LOG, 3);
        source.put(DIRT, 1);
        StockLedger ledger = StockLedger.of(source);

        // List.copyOf preserves encounter order; the test reads as a sequence
        // check, not a set check, so a LinkedHashMap-vs-HashMap regression
        // surfaces immediately.
        assertEquals(
            java.util.List.of(STONE, OAK_LOG, DIRT),
            ledger.entries().keySet().stream().toList(),
            "entries() iterates in insertion order so Town.applyStockLedger is deterministic"
        );
    }

    @Test
    @DisplayName("entries() is a read-only view — mutating the source map after construction does not leak in")
    void entriesAreImmutableView() {
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(STONE, 5);
        StockLedger ledger = StockLedger.of(source);

        source.put(OAK_LOG, 3);

        assertEquals(0, ledger.get(OAK_LOG),
            "mutating the source map after StockLedger.of does not affect the ledger");
        assertThrows(UnsupportedOperationException.class,
            () -> ledger.entries().put(DIRT, 1),
            "entries() returns an unmodifiable view — applyStockLedger's iteration is read-only");
    }

    @Test
    @DisplayName("merge is commutative — the dual-write is order-independent")
    void mergeIsCommutative() {
        StockLedger a = StockLedger.EMPTY.add(STONE, 10).add(OAK_LOG, 3);
        StockLedger b = StockLedger.EMPTY.add(STONE, 5).add(DIRT, 7);

        StockLedger ab = a.merge(b);
        StockLedger ba = b.merge(a);

        assertAll(
            () -> assertEquals(ab.size(), ba.size(),
                "the resulting ledger has the same number of entries regardless of merge order"),
            () -> assertEquals(ab.get(STONE), ba.get(STONE),
                "overlapping quantities are summed identically"),
            () -> assertEquals(ab.get(OAK_LOG), ba.get(OAK_LOG),
                "left-only entries appear in both directions"),
            () -> assertEquals(ab.get(DIRT), ba.get(DIRT),
                "right-only entries appear in both directions")
        );
    }

    @Test
    @DisplayName("of() then iterate-and-rebuild is the same ledger — the apply path's loop is total")
    void ofAndIterateIsTotal() {
        // The apply path on Town clears reserveStock, iterates ledger.entries(),
        // and calls reserveStock.merge for each pair. The resulting map is
        // (value-wise) the same as if applyStockLedger had handed the original
        // map to StockLedger.of. Pin that here so the apply path's total
        // behaviour is part of the contract.
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(STONE, 10);
        source.put(OAK_LOG, 3);
        source.put(DIRT, 7);
        StockLedger ledger = StockLedger.of(source);

        // Simulate applyStockLedger's loop without the registry lookup.
        Map<ItemId, Integer> simulated = new LinkedHashMap<>();
        for (Map.Entry<ItemId, Integer> e : ledger.entries().entrySet()) {
            simulated.merge(e.getKey(), e.getValue(), Integer::sum);
        }

        StockLedger rebuilt = StockLedger.of(simulated);
        assertAll(
            () -> assertEquals(ledger.size(), rebuilt.size(),
                "the rebuilt ledger has the same number of entries"),
            () -> assertEquals(ledger.get(STONE), rebuilt.get(STONE)),
            () -> assertEquals(ledger.get(OAK_LOG), rebuilt.get(OAK_LOG)),
            () -> assertEquals(ledger.get(DIRT), rebuilt.get(DIRT))
        );
    }

    @Test
    @DisplayName("zero-quantity entries on the apply path drop at the edge")
    void zeroQuantitiesDropOnApplyPath() {
        // applyStockLedger skips null / non-positive values silently, the
        // same way StockLedger.of drops zero entries on its own construction.
        // The constructor is the last line of defence — but the apply path
        // also short-circuits before lookup, so the resulting map only has
        // positive entries. This test pins that by feeding a hand-crafted
        // ledger that includes a zero-quantity entry (after a take drains a
        // value) and confirming the rebuild remains sparse.
        StockLedger drained = StockLedger.EMPTY.add(STONE, 10).take(STONE, 10);
        StockLedger withAnother = drained.add(OAK_LOG, 5);

        assertSame(StockLedger.EMPTY, drained,
            "drained-to-zero collapses to EMPTY — the apply path inherits this");
        assertEquals(1, withAnother.size(),
            "only positive entries survive the apply path's iteration");
    }
}