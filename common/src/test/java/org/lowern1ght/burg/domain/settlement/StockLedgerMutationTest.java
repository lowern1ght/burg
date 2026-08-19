package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link StockLedger}, including the
 * apply-wire round trip the {@code Town.applyStockLedger} dual-write
 * (ADR-0013) relies on: the entries() view is the canonical wire, a
 * ledger rebuilt from the wire equals the original, the second pass is a
 * fixpoint, and only positive quantities ever appear on the wire.
 *
 * <p>One deliberate deviation from the "merge is idempotent" reading:
 * merging a <em>non-empty</em> ledger twice contributes it twice (sum
 * semantics — {@link StockLedger#merge} sums quantities). Idempotency
 * holds exactly when the right side is EMPTY, which is the identity
 * element. The doubling test below pins the sum semantics so a mutant
 * that switches merge to put-if-absent dies.
 */
class StockLedgerMutationTest {

    private static ItemId item(String raw) {
        return ItemId.of(raw);
    }

    private static final ItemId STONE = item("minecraft:stone");
    private static final ItemId OAK_LOG = item("minecraft:oak_log");
    private static final ItemId DIRT = item("minecraft:dirt");

    /**
     * Simulates the Town-side wire without Minecraft: the apply path
     * clears the persisted map and re-merges every entry from the
     * ledger's canonical {@code entries()} view.
     */
    private static Map<ItemId, Integer> crossTheWire(StockLedger ledger) {
        Map<ItemId, Integer> wire = new LinkedHashMap<>();
        for (Map.Entry<ItemId, Integer> e : ledger.entries().entrySet()) {
            wire.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return wire;
    }

    @Test
    @DisplayName("add(stone,5) twice equals add(stone,10) once")
    void addTwiceEqualsSumOnce() {
        StockLedger byTwo = StockLedger.EMPTY.add(STONE, 5).add(STONE, 5);
        StockLedger byOne = StockLedger.EMPTY.add(STONE, 10);

        assertAll(
            () -> assertEquals(byOne.size(), byTwo.size()),
            () -> assertEquals(byOne.get(STONE), byTwo.get(STONE)),
            () -> assertEquals(byOne.entries(), byTwo.entries(),
                "the two ledgers are value-identical on the wire")
        );
    }

    @Test
    @DisplayName("add(item, 0) is a no-op — same instance returned")
    void addZeroIsNoOp() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 7);

        assertSame(ledger, ledger.add(STONE, 0),
            "a zero-amount add must not rebuild the ledger");
        assertSame(StockLedger.EMPTY, StockLedger.EMPTY.add(STONE, 0));
    }

    @Test
    @DisplayName("add then take the same amount returns to EMPTY — same instance")
    void addThenTakeCancels() {
        StockLedger roundTripped = StockLedger.EMPTY.add(STONE, 7).take(STONE, 7);

        assertSame(StockLedger.EMPTY, roundTripped,
            "a ledger drained back to nothing collapses to the sentinel");

        StockLedger twoItems = StockLedger.EMPTY.add(STONE, 3).add(OAK_LOG, 4);
        assertSame(StockLedger.EMPTY, twoItems.take(OAK_LOG, 4).take(STONE, 3),
            "cancellation across several items still collapses to EMPTY");
    }

    @Test
    @DisplayName("take leaves zero-qty entries off the roll but keeps untouched siblings")
    void takeDropsOnlyTheDrainedEntry() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 5).add(OAK_LOG, 3);
        StockLedger drained = ledger.take(STONE, 5);

        assertAll(
            () -> assertEquals(1, drained.size(), "stone dropped, oak kept"),
            () -> assertEquals(0, drained.get(STONE)),
            () -> assertEquals(3, drained.get(OAK_LOG))
        );
    }

    @Test
    @DisplayName("merge with EMPTY on the right is the identity — same instance, stable when repeated")
    void mergeWithEmptyIsIdentity() {
        StockLedger left = StockLedger.EMPTY.add(STONE, 10);

        StockLedger once = left.merge(StockLedger.EMPTY);
        StockLedger twice = once.merge(StockLedger.EMPTY);

        assertAll(
            () -> assertSame(left, once,
                "merging the EMPTY ledger returns the receiver unchanged"),
            () -> assertSame(left, twice,
                "merging EMPTY twice is still the receiver — identity is idempotent")
        );
    }

    @Test
    @DisplayName("merge with EMPTY on the left reproduces the right side value-wise")
    void emptyMergeReproducesRight() {
        StockLedger right = StockLedger.EMPTY.add(STONE, 10).add(OAK_LOG, 3);
        StockLedger merged = StockLedger.EMPTY.merge(right);

        assertAll(
            () -> assertEquals(right.size(), merged.size()),
            () -> assertEquals(right.get(STONE), merged.get(STONE)),
            () -> assertEquals(right.get(OAK_LOG), merged.get(OAK_LOG)),
            () -> assertEquals(right.entries(), merged.entries())
        );
    }

    @Test
    @DisplayName("merging a non-empty ledger twice contributes it twice — sum semantics, NOT idempotent")
    void mergeNonEmptyTwiceDoubles() {
        StockLedger left = StockLedger.EMPTY.add(STONE, 10);
        StockLedger right = StockLedger.EMPTY.add(STONE, 5);

        StockLedger once = left.merge(right);
        StockLedger twice = once.merge(right);

        assertEquals(20, twice.get(STONE),
            "merge sums quantities, so a second merge adds right's stock again "
                + "(10+5=15, then 15+5=20). A put-if-absent mutant dies here.");
    }

    @Test
    @DisplayName("merge is commutative value-wise")
    void mergeIsCommutativeValueWise() {
        StockLedger a = StockLedger.EMPTY.add(STONE, 10).add(OAK_LOG, 3);
        StockLedger b = StockLedger.EMPTY.add(STONE, 5).add(DIRT, 7);

        assertAll(
            () -> assertEquals(a.merge(b).entries(), b.merge(a).entries()),
            () -> assertEquals(a.merge(b).size(), b.merge(a).size())
        );
    }

    @Test
    @DisplayName("wire round trip: entries → wire map → of() rebuilds an equal ledger")
    void wireRoundTripRebuildsEqual() {
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(STONE, 10);
        source.put(OAK_LOG, 3);
        source.put(DIRT, 7);
        StockLedger original = StockLedger.of(source);

        StockLedger rebuilt = StockLedger.of(crossTheWire(original));

        assertAll(
            () -> assertEquals(original.size(), rebuilt.size()),
            () -> assertEquals(original.get(STONE), rebuilt.get(STONE)),
            () -> assertEquals(original.get(OAK_LOG), rebuilt.get(OAK_LOG)),
            () -> assertEquals(original.get(DIRT), rebuilt.get(DIRT)),
            () -> assertEquals(original.entries(), rebuilt.entries())
        );
    }

    @Test
    @DisplayName("second pass through the wire is a fixpoint")
    void wireRoundTripIsFixpoint() {
        StockLedger original = StockLedger.EMPTY.add(STONE, 10).add(OAK_LOG, 3);
        StockLedger first = StockLedger.of(crossTheWire(original));
        StockLedger second = StockLedger.of(crossTheWire(first));

        assertAll(
            () -> assertEquals(first.entries(), second.entries(),
                "crossing the wire a second time changes nothing"),
            () -> assertEquals(first.size(), second.size())
        );
    }

    @Test
    @DisplayName("EMPTY crosses the wire as an empty map and rebuilds as the EMPTY sentinel")
    void emptyCrossesAsEmpty() {
        Map<ItemId, Integer> wire = crossTheWire(StockLedger.EMPTY);

        assertTrue(wire.isEmpty(), "the EMPTY ledger puts nothing on the wire");
        assertSame(StockLedger.EMPTY, StockLedger.of(wire),
            "an empty wire rebuilds as the referentially-stable sentinel");
    }

    @Test
    @DisplayName("only positive entries ever appear on the wire — zeros, negatives and nulls drop at of()")
    void onlyPositiveEntriesOnWire() {
        Map<ItemId, Integer> dirty = new LinkedHashMap<>();
        dirty.put(STONE, 0);
        dirty.put(OAK_LOG, -4);
        dirty.put(DIRT, null);

        StockLedger cleaned = StockLedger.of(dirty);

        assertAll(
            () -> assertTrue(cleaned.isEmpty(),
                "a source of only zero/negative/null values collapses to EMPTY"),
            () -> assertEquals(0, cleaned.get(STONE)),
            () -> assertEquals(0, cleaned.get(OAK_LOG)),
            () -> assertEquals(0, cleaned.get(DIRT))
        );

        // And no sequence of mutators can produce a non-positive entry either.
        StockLedger worked = StockLedger.EMPTY
            .add(STONE, 5).add(STONE, 2)
            .add(OAK_LOG, 3).take(OAK_LOG, 3)
            .add(DIRT, 1);
        assertTrue(worked.entries().values().stream().allMatch(q -> q > 0),
            "every entry that survives mutation is strictly positive");
        assertFalse(worked.entries().containsKey(OAK_LOG),
            "the drained-to-zero item is not on the wire");
    }

    @Test
    @DisplayName("a null key on a hand-built source is rejected, not silently wrapped")
    void nullKeyRejectedAtOf() {
        Map<ItemId, Integer> withNullKey = new LinkedHashMap<>();
        withNullKey.put(null, 5);

        assertThrows(NullPointerException.class, () -> StockLedger.of(withNullKey));
    }

    @Test
    @DisplayName("take(item, 0) and take(item, -1) are rejected — zero is not a take")
    void takeRejectsNonPositive() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 5);

        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> ledger.take(STONE, 0)),
            () -> assertThrows(IllegalArgumentException.class, () -> ledger.take(STONE, -1)),
            () -> assertEquals(5, ledger.get(STONE), "the ledger survives the rejects")
        );
    }

    @Test
    @DisplayName("take one-short-of-stock fails and leaves the receiver intact")
    void takeOneShortFails() {
        StockLedger ledger = StockLedger.EMPTY.add(STONE, 5);

        assertThrows(IllegalStateException.class, () -> ledger.take(STONE, 6));
        assertEquals(5, ledger.get(STONE));
        assertEquals(1, ledger.size());
    }
}
