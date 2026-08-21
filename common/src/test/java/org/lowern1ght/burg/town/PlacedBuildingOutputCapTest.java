package org.lowern1ght.burg.town;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.infrastructure.config.BuildingOutputCap;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-JVM pin for the per-instance output cap FIFO discipline
 * (ADR-0027 / {@code BurgConfig.BUILDING_OUTPUT_CAP_PER_INSTANCE}). The
 * cap is enforced at every growth edge of {@link PlacedBuilding} —
 * {@code forceAdd}, {@code produce}, and the load-path
 * {@code syncOutputLedgerFromStock} — through the package-private static
 * helper {@code PlacedBuilding.applyOutputCap}.
 *
 * <p>This test exercises the helper directly on a bare-JVM
 * {@link StockLedger}, bypassing the MC {@code BuiltInRegistries.ITEM}
 * lookup that {@code forceAdd} / {@code produce} need. The FIFO
 * discipline (oldest entries dropped first, ledger stays sparse) is
 * the part that's pure-domain arithmetic on {@link StockLedger}; the
 * MC-side mirror on the {@code stock} map is pinned by the MC-aware
 * test in {@code :neoforge:test}'s
 * {@code PlacedBuildingForceAddOutputCapTest}.
 *
 * <p>What this pins:
 * <ol>
 *   <li><b>FIFO drop on overflow.</b> With cap=2 and three adds in
 *       insertion order, the post-cap ledger carries the two newest
 *       entries and the oldest is gone — no surprise eviction of the
 *       freshly-added item.</li>
 *   <li><b>Insertion-order preservation.</b> After the drain the
 *       surviving entries are in the order they were originally added;
 *       the FIFO loop does not reorder the ledger.</li>
 *   <li><b>No-op below cap.</b> A ledger of size &lt; cap passes
 *       through untouched; the helper does not invent drops on a
 *       ledger that fits.</li>
 *   <li><b>Drop count via the Consumer hook.</b> The {@code onDrop}
 *       sink is invoked once per dropped entry, in FIFO order, so the
 *       instance mutators can mirror each drop onto the legacy
 *       {@code stock} map.</li>
 *   <li><b>Integer.MAX_VALUE sentinel is "no cap".</b> When
 *       {@link BuildingOutputCap#current()} exposes
 *       {@code Integer.MAX_VALUE}, the helper is a pass-through and
 *       the ledger survives regardless of size.</li>
 *   <li><b>The default cap (256) leaves a 3-entry ledger untouched.</b>
 *       The additive default for fresh saves does not drain normal
 *       amounts; the FIFO discipline is the cap-fitted edge, not the
 *       steady state.</li>
 * </ol>
 */
class PlacedBuildingOutputCapTest {

    @AfterEach
    void resetBuildingOutputCap() {
        // The `current()` slot is volatile and process-shared; restore
        // the additive default so a `setCurrent` in one case does not
        // leak into the next. Same discipline RaidConfigTest and
        // RaidManagerTest use.
        BuildingOutputCap.resetCurrent();
    }

    @Test
    @DisplayName("applyOutputCap(StockLedger, Consumer) — FIFO drops the oldest entry when size exceeds cap (cap=2, three adds)")
    void fifoDropsOldestWhenOverfull() {
        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));

        // Build a ledger with three entries in insertion order. Wheat is
        // the oldest; potato is the newest.
        StockLedger ledger = StockLedger.EMPTY
            .add(ItemId.of("minecraft:wheat"), 1)
            .add(ItemId.of("minecraft:carrot"), 2)
            .add(ItemId.of("minecraft:potato"), 3);
        assertEquals(3, ledger.size(),
            "pre-cap: ledger carries all three entries — the additive after three adds");

        StockLedger capped = PlacedBuilding.applyOutputCap(ledger, null);

        assertAll(
            () -> assertEquals(2, capped.size(),
                "post-cap: exactly two entries survive — the FIFO discipline drains to cap"),
            () -> assertEquals(0, capped.get(ItemId.of("minecraft:wheat")),
                "wheat is dropped — it was the oldest entry at FIFO time"),
            () -> assertEquals(2, capped.get(ItemId.of("minecraft:carrot")),
                "carrot survives — newer than wheat, the only FIFO drop"),
            () -> assertEquals(3, capped.get(ItemId.of("minecraft:potato")),
                "potato survives — newest, added last, beyond the FIFO cut"),
            () -> assertFalse(capped.entries().containsKey(ItemId.of("minecraft:wheat")),
                "the dropped entry is absent from entries() — sparse discipline preserved")
        );
    }

    @Test
    @DisplayName("applyOutputCap — surviving entries keep insertion order; the FIFO loop does not reorder")
    void insertionOrderPreservedAfterDrain() {
        // With cap=2 and four entries, the FIFO loop drains the two
        // OLDEST entries (wheat then carrot). Survivors are potato and
        // beetroot, in their original insertion order. The loop must
        // not reorder the ledger as it drains.
        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));

        StockLedger ledger = StockLedger.EMPTY
            .add(ItemId.of("minecraft:wheat"), 1)
            .add(ItemId.of("minecraft:carrot"), 2)
            .add(ItemId.of("minecraft:potato"), 3)
            .add(ItemId.of("minecraft:beetroot"), 4);

        StockLedger capped = PlacedBuilding.applyOutputCap(ledger, null);

        assertEquals(2, capped.size(),
            "post-cap: 4 -> 2 (wheat + carrot dropped oldest-first)");
        List<ItemId> order = new ArrayList<>(capped.entries().keySet());
        assertEquals(ItemId.of("minecraft:potato"), order.get(0),
            "first survivor is potato — it was inserted before beetroot, FIFO preserved order");
        assertEquals(ItemId.of("minecraft:beetroot"), order.get(1),
            "second survivor is beetroot — the newest at FIFO time, kept in place");
    }

    @Test
    @DisplayName("applyOutputCap — a ledger of size <= cap is the identity; no drops, no reordering")
    void belowCapIsPassThrough() {
        // Default cap is 256; a 3-entry ledger is well under it.
        assertEquals(256, BuildingOutputCap.DEFAULT.items(),
            "default cap is 256 — the additive default for fresh saves");

        StockLedger ledger = StockLedger.EMPTY
            .add(ItemId.of("minecraft:wheat"), 1)
            .add(ItemId.of("minecraft:carrot"), 2)
            .add(ItemId.of("minecraft:potato"), 3);

        StockLedger capped = PlacedBuilding.applyOutputCap(ledger, null);

        assertSame(ledger, capped,
            "below cap: the helper returns the candidate unchanged (no new ledger allocated, no drops)");
    }

    @Test
    @DisplayName("applyOutputCap — the onDrop Consumer sees each dropped ItemId in FIFO order (oldest first)")
    void consumerReceivesDropsInFifoOrder() {
        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));

        StockLedger ledger = StockLedger.EMPTY
            .add(ItemId.of("minecraft:wheat"), 1)
            .add(ItemId.of("minecraft:carrot"), 2)
            .add(ItemId.of("minecraft:potato"), 3);

        List<ItemId> dropped = new ArrayList<>();
        PlacedBuilding.applyOutputCap(ledger, dropped::add);

        assertEquals(1, dropped.size(),
            "one entry was dropped (cap=2 with 3 entries)");
        assertEquals(ItemId.of("minecraft:wheat"), dropped.get(0),
            "the dropped entry is wheat — the oldest at FIFO time");
    }

    @Test
    @DisplayName("applyOutputCap — when the cap is Integer.MAX_VALUE, the helper is a pass-through (no drain)")
    void integerMaxValueIsUnlimited() {
        // Bypass the spec's range clamp — BuildingOutputCap(int) clamps
        // to [16, 4096], but we want to exercise the documented
        // Integer.MAX_VALUE sentinel path. The ctor clamps to MAX_ITEMS
        // = 4096 on its own, so we reflectively widen the field to
        // MAX_VALUE — the helper treats MAX_VALUE as the unlimited
        // sentinel. This keeps the production code path honest (no
        // ctor overload that takes MAX_VALUE) while still letting the
        // test pin the sentinel branch.
        BuildingOutputCap unlimited;
        try {
            java.lang.reflect.Field itemsField = BuildingOutputCap.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            unlimited = new BuildingOutputCap(BuildingOutputCap.MAX_ITEMS);
            itemsField.setInt(unlimited, Integer.MAX_VALUE);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not construct Integer.MAX_VALUE sentinel", e);
        }
        BuildingOutputCap.setCurrent(unlimited);

        StockLedger ledger = StockLedger.EMPTY
            .add(ItemId.of("minecraft:wheat"), 1)
            .add(ItemId.of("minecraft:carrot"), 2)
            .add(ItemId.of("minecraft:potato"), 3)
            .add(ItemId.of("minecraft:beetroot"), 4)
            .add(ItemId.of("minecraft:melon"), 5);

        StockLedger capped = PlacedBuilding.applyOutputCap(ledger, null);

        assertSame(ledger, capped,
            "MAX_VALUE sentinel: helper is a pass-through — the unlimited behaviour survives intact");
        assertEquals(5, capped.size(),
            "all five entries survive — no FIFO drop under the sentinel");
    }

    @Test
    @DisplayName("applyOutputCap — additive default (cap=256) leaves a 3-entry ledger untouched on a fresh install")
    void defaultCapLeavesNormalLedgersAlone() {
        // The additive default is what `new BuildingOutputCap(256)`
        // produces. Without a setCurrent call, the static `current()`
        // slot starts at DEFAULT — pin both the static slot value and
        // the helper's behaviour on a ledger that fits.
        assertSame(BuildingOutputCap.DEFAULT, BuildingOutputCap.current(),
            "current() starts at DEFAULT on a fresh class load — no test residue leaks");

        StockLedger ledger = StockLedger.EMPTY
            .add(ItemId.of("minecraft:wheat"), 1)
            .add(ItemId.of("minecraft:carrot"), 2)
            .add(ItemId.of("minecraft:potato"), 3);

        StockLedger capped = PlacedBuilding.applyOutputCap(ledger, null);

        assertSame(ledger, capped,
            "default cap (256) is comfortably above any realistic per-building variety — "
                + "the FIFO loop never fires");
        assertEquals(256, BuildingOutputCap.current().items(),
            "the static slot's items() value matches the spec default — "
                + "BurgConfig.refreshBuildingOutputCap will push this on mod-bus init");
        assertTrue(BuildingOutputCap.DEFAULT.items() <= 4096,
            "default cap is at or under the spec ceiling (4096) — never above");
    }
}
