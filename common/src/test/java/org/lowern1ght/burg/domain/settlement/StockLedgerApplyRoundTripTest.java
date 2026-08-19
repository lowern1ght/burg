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

/**
 * Pins the wire-format behaviour the {@code _supply_} packet
 * (ADR-0013 + PR #42 / commit {@code dc31057}) relies on: the server
 * side translates a one-item {@code C2SSupplyStockPacket} into a
 * {@link StockLedger} of size 1 and "applies" it to the town's
 * StockLedger by merge. This is the only path on which the additive
 * supply write reaches the domain view, so its merge semantics have
 * to hold exactly.
 *
 * <p>Five invariants are pinned here:
 * <ol>
 *   <li>two-item reserve merged with one-item supply yields the correct
 *       totals — the other item is untouched, the shared item sums;</li>
 *   <li>equality holds across the resulting ledger and an equivalent
 *       ledger built from the same wire (a {@code StockLedger} built
 *       from the same wire rebuilds to the same value);</li>
 *   <li>an EMPTY supply merge is a no-op — same instance returned, the
 *       town ledger is the identity element of the merge;</li>
 *   <li>a supply whose source map has a zero-qty item gets that item
 *       dropped at construction (the same sparse discipline merge
 *       applies on the inside — see the {@code summed == 0} branch
 *       that is unreachable from the public API but kept as a defensive
 *       invariant);</li>
 *   <li>a fresh supply against EMPTY reproduces the supply's wire —
 *       first-supply symmetry.</li>
 * </ol>
 *
 * <p>No Minecraft imports — same bare-JVM discipline as the rest of
 * the {@code domain.settlement} test package.
 */
class StockLedgerApplyRoundTripTest {

    private static ItemId item(String raw) {
        return ItemId.of(raw);
    }

    private static final ItemId STONE = item("minecraft:stone");
    private static final ItemId OAK_LOG = item("minecraft:oak_log");
    private static final ItemId WHEAT = item("minecraft:wheat");

    /**
     * The packet path on the server is "read quantity for one ItemId,
     * merge into reserve" — modelled here without Minecraft as a
     * single-entry {@link StockLedger} built from a one-row map.
     * Mirrors {@code C2SSupplyStockPacket} payload after registry
     * resolution: the item survived the registry lookup and the
     * quantity passed the &gt; 0 guard, so we have a definite one-entry
     * ledger to merge.
     */
    private static StockLedger simulateSupplyPacket(ItemId item, int quantity) {
        Map<ItemId, Integer> oneRow = new LinkedHashMap<>();
        oneRow.put(item, quantity);
        return StockLedger.of(oneRow);
    }

    @Test
    @DisplayName("L1 = {stone=10, oak=3}, supply=stone+5 ⇒ totals are {stone=15, oak=3} untouched sibling is preserved")
    void mergeWithSupplyPacketSumsOnlyTheMatchingItem() {
        // L1 simulates the town's two-item reserve stock at the moment
        // the packet arrives. Built via the public API (StockLedger.of)
        // so the test reads like the real wire path, not a back-door
        // set of entries.
        Map<ItemId, Integer> l1Source = new LinkedHashMap<>();
        l1Source.put(STONE, 10);
        l1Source.put(OAK_LOG, 3);
        StockLedger l1 = StockLedger.of(l1Source);

        StockLedger supply = simulateSupplyPacket(STONE, 5);

        StockLedger after = l1.merge(supply);

        Map<ItemId, Integer> expectedWire = new LinkedHashMap<>();
        expectedWire.put(STONE, 15);
        expectedWire.put(OAK_LOG, 3);

        assertAll(
            () -> assertEquals(15, after.get(STONE),
                "the matching item summed (10 + 5)"),
            () -> assertEquals(3, after.get(OAK_LOG),
                "the untouched sibling is preserved"),
            () -> assertEquals(2, after.size(),
                "no new entry appeared; the supply was one of the two"),
            () -> assertEquals(expectedWire, after.entries(),
                "the resulting wire is exactly the expected map after the merge")
        );
    }

    @Test
    @DisplayName("L1.merge(supply).entries() equals a fresh ledger rebuilt from the same wire")
    void mergeIsConsistentWithRebuildFromWire() {
        Map<ItemId, Integer> l1Source = new LinkedHashMap<>();
        l1Source.put(STONE, 10);
        l1Source.put(OAK_LOG, 3);
        StockLedger l1 = StockLedger.of(l1Source);

        StockLedger supply = simulateSupplyPacket(STONE, 5);
        StockLedger after = l1.merge(supply);

        // Rebuild the wire view as an independent StockLedger. If the
        // merge is value-stable, the two must compare equal on every
        // observable property — entries(), size(), get() for every
        // distinct item.
        StockLedger rebuilt = StockLedger.of(new LinkedHashMap<>(after.entries()));

        assertAll(
            () -> assertEquals(rebuilt.entries(), after.entries(),
                "the merge produces the same wire view as a rebuild from the same wire"),
            () -> assertEquals(rebuilt.size(), after.size()),
            () -> assertEquals(rebuilt.get(STONE), after.get(STONE)),
            () -> assertEquals(rebuilt.get(OAK_LOG), after.get(OAK_LOG)),
            () -> assertEquals(15, after.get(STONE)),
            () -> assertEquals(3, after.get(OAK_LOG))
        );
    }

    @Test
    @DisplayName("L1.merge(EMPTY) returns L1 unchanged — same instance, EMPTY is a true no-op")
    void emptySupplyMergeIsNoOp() {
        // L1 has a non-trivial reserve so the assertion has teeth.
        StockLedger l1 = StockLedger.of(new LinkedHashMap<>(Map.of(STONE, 10, OAK_LOG, 3)));

        StockLedger once = l1.merge(StockLedger.EMPTY);
        StockLedger twice = once.merge(StockLedger.EMPTY);

        assertAll(
            () -> assertSame(l1, once,
                "merging EMPTY returns the receiver instance — EMPTY is the merge identity"),
            () -> assertSame(l1, twice,
                "merging EMPTY twice is still the receiver — identity is idempotent"),
            () -> assertEquals(2, once.size()),
            () -> assertEquals(10, once.get(STONE))
        );
    }

    @Test
    @DisplayName("a supply whose source map contains a zero-qty item drops it at construction — same sparse discipline merge applies on the summed==0 branch")
    void mergeThatDropsZeroQtyAtSourceKeepsTheSparseInvariant() {
        // StockLedger.of filters out zero and negative quantities at
        // construction (same defensive contract the merge() summed==0
        // branch applies on the inside). The summed==0 branch of merge
        // is unreachable from the public API because of() refuses to
        // build a ledger with a negative on either side, but the
        // construction-time drop is the same discipline and the only
        // externally observable manifestation of the sparse rule on
        // the wire. Pin it through a supply source that mixes zero
        // and positive entries.
        Map<ItemId, Integer> dirtySource = new LinkedHashMap<>();
        dirtySource.put(STONE, 0);    // zero entry — dropped at of()
        dirtySource.put(OAK_LOG, 5);  // positive — kept
        StockLedger supply = StockLedger.of(dirtySource);

        StockLedger l1 = StockLedger.EMPTY.add(STONE, 10).add(OAK_LOG, 3);
        StockLedger after = l1.merge(supply);

        assertAll(
            () -> assertFalse(supply.entries().containsKey(STONE),
                "the zero entry never made it onto the supply wire"),
            () -> assertEquals(10, after.get(STONE),
                "stone is untouched (the dropped entry never reached the merge)"),
            () -> assertEquals(8, after.get(OAK_LOG),
                "oak summed (3 + 5) — only the surviving entry contributes"),
            () -> assertEquals(2, after.size(),
                "no ghost zero-qty entry survives the construction-time filter")
        );
    }

    @Test
    @DisplayName("a fresh supply against EMPTY builds exactly the supply's wire — first-supply symmetry")
    void firstSupplyAgainstEmptyLedger() {
        // First ever supply packet on a brand-new town: the receiver is
        // EMPTY, the supply is a one-row ledger. The result equals the
        // supply value-wise.
        StockLedger emptyTown = StockLedger.EMPTY;
        StockLedger firstSupply = simulateSupplyPacket(WHEAT, 8);

        StockLedger after = emptyTown.merge(firstSupply);

        assertAll(
            () -> assertEquals(8, after.get(WHEAT),
                "the supply's quantity is the resulting quantity"),
            () -> assertEquals(1, after.size(),
                "only the supplied item appears on the wire"),
            () -> assertEquals(firstSupply.entries(), after.entries(),
                "EMPTY.merge(supply) reproduces the supply's wire")
        );
    }
}