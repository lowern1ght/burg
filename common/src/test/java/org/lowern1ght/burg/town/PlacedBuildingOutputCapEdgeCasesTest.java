package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.infrastructure.config.BuildingOutputCap;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge-case pins for the per-instance output cap FIFO discipline
 * (ADR-0027 / {@code BurgConfig.BUILDING_OUTPUT_CAP_PER_INSTANCE}).
 * Follows the {@link PlacedBuildingOutputCapTest} bare-JVM coverage and
 * the {@link PlacedBuildingLockstepTest} lockstep coverage; this file
 * closes the "what about the boundaries the existing suite didn't
 * exercise" gap the worker #62 commit flagged as deferred.
 *
 * <p><b>The edges pinned here.</b>
 *
 * <ol>
 *   <li><b>{@code cap=0} and {@code cap=1} clamp behavior.</b>
 *       {@link BuildingOutputCap#BuildingOutputCap(int)} clamps via
 *       {@code Math.max(MIN_ITEMS=2, Math.min(MAX_ITEMS=4096, items))} —
 *       the {@code MIN_ITEMS=2} floor is documented as
 *       <i>"Two is the smallest cap that still leaves meaningful FIFO
 *       semantics"</i>. {@code cap=0} and {@code cap=1} are therefore
 *       unreachable through the public value object; both clamp to
 *       {@code items()==2}. The edge-case test pins the clamp itself
 *       and asserts the FIFO discipline fires on the third add (the
 *       smallest case that drains under the clamped ceiling).</li>
 *
 *   <li><b>Cap reset mid-life.</b> {@code forceAdd} reads
 *       {@link BuildingOutputCap#current()} on every call, so a
 *       {@link BuildingOutputCap#setCurrent} between calls is observed
 *       by the next write. The Cloth config screen pushes a new
 *       cap on reload (and would mid-game if a player edited it),
 *       so the next {@code forceAdd} must size against the new value,
 *       not the one in effect at construction. Pin: with
 *       {@code cap=3} the third add fits at-cap; lowering to
 *       {@code cap=2} and adding a fourth distinct item FIFO-drops
 *       the oldest — without any code-path-aware tricks.</li>
 *
 *   <li><b>Mixed add + drain lockstep.</b> {@code forceAdd} and
 *       {@code drain} both update the SoT ledger and the legacy MC
 *       mirror, but only the FIFO-drop hook on {@code forceAdd}
 *       cleans zero-quantity entries out of the mirror. {@code drain}
 *       uses {@code stock.put(item, available - taken)} — when a full
 *       drain zeroes the entry, the ledger drops it (sparse
 *       discipline) but the mirror keeps the key at value 0. This
 *       file pins that exact behavior: per-item quantities stay in
 *       lockstep on the partial-drain path; on the full-drain path
 *       the quantities match at 0 but the mirror retains the key.
 *       The asymmetry is a known, documented lockstep edge, not a
 *       bug — the FIFO-drop path covers the FIFO drain; the
 *       {@code drain} edge is the place a future carve that wants
 *       sparse mirror keys (call it {@code drain → stock.remove} on
 *       full drain) will land.</li>
 * </ol>
 *
 * <p>The cap-reset and mixed add+remove cases reach
 * {@link PlacedBuilding#forceAdd(Item, int)} and
 * {@link PlacedBuilding#drain(Item, int)}, which require the MC
 * {@link BuiltInRegistries#ITEM} registry to be reachable. Same seam
 * the existing {@link PlacedBuildingLockstepTest} uses:
 * {@link net.minecraft.server.Bootstrap#isBootstrapped} relaxed
 * reflectively, four fresh {@link Item} instances
 * ({@code new Item(Properties)}) registered into a private namespace
 * ({@code placecapedge:test_a..d}). The constructor-clamp cases
 * (cases 1a and 1b) have no MC dependency and run regardless of the
 * bootstrap state.
 */
class PlacedBuildingOutputCapEdgeCasesTest {

    private static Item TEST_A;
    private static Item TEST_B;
    private static Item TEST_C;
    private static Item TEST_D;

    private static final ResourceLocation KEY_A =
        ResourceLocation.fromNamespaceAndPath("placecapedge", "test_a");
    private static final ResourceLocation KEY_B =
        ResourceLocation.fromNamespaceAndPath("placecapedge", "test_b");
    private static final ResourceLocation KEY_C =
        ResourceLocation.fromNamespaceAndPath("placecapedge", "test_c");
    private static final ResourceLocation KEY_D =
        ResourceLocation.fromNamespaceAndPath("placecapedge", "test_d");

    @BeforeAll
    static void bootstrapAndRegisterItems() throws Exception {
        // Same seam as PlacedBuildingLockstepTest. Mark Bootstrap as
        // called before any registry read; the production code's only
        // registry path goes through Bootstrap.checkBootstrapCalled.
        // Items.<clinit> still trips downstream on DataFixers.<clinit>
        // ("Game version not set"), but every Item here is a fresh
        // `new Item(Properties)` that bypasses the Items constants.
        Field isBootstrapped = Class.forName("net.minecraft.server.Bootstrap")
            .getDeclaredField("isBootstrapped");
        isBootstrapped.setAccessible(true);
        isBootstrapped.setBoolean(null, true);

        Class<?> propsClass = Class.forName("net.minecraft.world.item.Item$Properties");
        TEST_A = new Item((Item.Properties) propsClass.getDeclaredConstructor().newInstance());
        TEST_B = new Item((Item.Properties) propsClass.getDeclaredConstructor().newInstance());
        TEST_C = new Item((Item.Properties) propsClass.getDeclaredConstructor().newInstance());
        TEST_D = new Item((Item.Properties) propsClass.getDeclaredConstructor().newInstance());

        Registry.register(BuiltInRegistries.ITEM, KEY_A, TEST_A);
        Registry.register(BuiltInRegistries.ITEM, KEY_B, TEST_B);
        Registry.register(BuiltInRegistries.ITEM, KEY_C, TEST_C);
        Registry.register(BuiltInRegistries.ITEM, KEY_D, TEST_D);
    }

    @AfterEach
    void resetBuildingOutputCap() {
        // Process-shared slot — restore the additive default so a
        // setCurrent in one case does not leak into the next. Same
        // discipline PlacedBuildingOutputCapTest +
        // PlacedBuildingLockstepTest use.
        BuildingOutputCap.resetCurrent();
    }

    private static PlacedBuilding freshBuilding() {
        return new PlacedBuilding(
            "cap_edge_test", BlockPos.ZERO,
            new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);
    }

    private static ItemId idFor(Item item) {
        return ItemId.of(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    // --------------------------------------------------------------------
    // Edge 1a — Cap=0 clamps to MIN_ITEMS=2 and the FIFO discipline
    // fires on the third add. Pin the documented floor: "Two is the
    // smallest cap that still leaves meaningful FIFO semantics".
    // --------------------------------------------------------------------

    @Test
    @DisplayName("cap=0 — constructor clamps to MIN_ITEMS=2; FIFO drains on the third distinct add")
    void capZeroClampsToMinimumAndFiresFifo() {
        BuildingOutputCap cap = new BuildingOutputCap(0);
        assertEquals(BuildingOutputCap.MIN_ITEMS, cap.items(),
            "cap=0 is clamped up to MIN_ITEMS=2 by the constructor — a 0-cap "
                + "would make every forceAdd a no-op, which the documented "
                + "MIN_ITEMS floor rules out");
        assertEquals(2, cap.items(),
            "the floor is 2, not 1 — 'Two is the smallest cap that still "
                + "leaves meaningful FIFO semantics'");

        BuildingOutputCap.setCurrent(cap);

        PlacedBuilding b = freshBuilding();
        b.forceAdd(TEST_A, 1);
        b.forceAdd(TEST_B, 2);
        b.forceAdd(TEST_C, 3);   // overflow -> FIFO drop A (oldest)

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        ItemId idC = idFor(TEST_C);
        assertAll(
            () -> assertEquals(2, b.outputLedger().size(),
                "post-cap: ledger has exactly 2 entries — the clamped floor drained A on the 3rd add"),
            () -> assertEquals(0, b.outputLedger().get(idA),
                "A is gone — FIFO drop on the clamped cap"),
            () -> assertEquals(2, b.outputLedger().get(idB),
                "B survives the FIFO drain"),
            () -> assertEquals(3, b.outputLedger().get(idC),
                "C is the newest — kept in place"),
            () -> assertFalse(b.getStockedItems().contains(TEST_A),
                "A is gone from the MC mirror — the lockstep holds"),
            () -> assertTrue(b.getStockedItems().contains(TEST_B)
                    && b.getStockedItems().contains(TEST_C),
                "B and C are on the MC mirror")
        );
    }

    // --------------------------------------------------------------------
    // Edge 1b — Cap=1 clamps identically to MIN_ITEMS=2; same FIFO
    // discipline, same drained shape as cap=0. Pin the floor from the
    // other direction so a future carve that accidentally special-cases
    // "cap below 2" gets caught.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("cap=1 — constructor clamps to MIN_ITEMS=2; FIFO drains on the third distinct add (same shape as cap=0)")
    void capOneClampsToMinimumAndFiresFifo() {
        BuildingOutputCap cap = new BuildingOutputCap(1);
        assertEquals(BuildingOutputCap.MIN_ITEMS, cap.items(),
            "cap=1 is clamped up to MIN_ITEMS=2 — 'two is the smallest cap' "
                + "is symmetric for 0 and 1; the constructor is a single "
                + "Math.max(MIN_ITEMS, ...) call");
        assertEquals(2, cap.items(),
            "the floor is 2, not 0 or 1 — a 1-cap would force strict single-slot "
                + "FIFO, which the production discipline rules out");

        BuildingOutputCap.setCurrent(cap);

        PlacedBuilding b = freshBuilding();
        b.forceAdd(TEST_A, 10);
        b.forceAdd(TEST_B, 20);
        b.forceAdd(TEST_C, 30);   // overflow -> FIFO drop A

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        ItemId idC = idFor(TEST_C);
        assertAll(
            () -> assertEquals(2, b.outputLedger().size(),
                "cap=1, clamped to 2: three adds drain to 2 — identical shape to cap=0"),
            () -> assertEquals(0, b.outputLedger().get(idA),
                "A is FIFO-dropped on the clamped cap"),
            () -> assertEquals(20, b.outputLedger().get(idB),
                "B's quantity survives the FIFO drain — the drain takes the "
                    + "full quantity of the dropped entry, not a partial amount"),
            () -> assertEquals(30, b.outputLedger().get(idC),
                "C's quantity survives — the FIFO drain targets the oldest "
                    + "entry, not arbitrary keys")
        );
    }

    // --------------------------------------------------------------------
    // Edge 2 — Cap reset mid-life. Cloth reloads push a new value into
    // the static slot; the next forceAdd must size against the new
    // value. With cap=3 the third add fits at-cap; lowering to cap=2
    // and adding a fourth distinct item FIFO-drops the oldest.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("cap reset between calls — the next forceAdd sizes against the new current(), draining to fit")
    void capResetBetweenCallsUsesNewCap() {
        // cap=3 — three distinct items fit at-cap, no FIFO drop.
        BuildingOutputCap.setCurrent(new BuildingOutputCap(3));
        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 1);   // [A=1]
        b.forceAdd(TEST_B, 2);   // [A=1, B=2]
        b.forceAdd(TEST_C, 3);   // [A=1, B=2, C=3]  — at cap, not over
        assertEquals(3, b.outputLedger().size(),
            "cap=3 with three adds: ledger is at cap, no FIFO drop");

        // Tighten the cap to 2 — simulates a Cloth reload mid-life.
        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));
        // 4th add: the candidate ledger grows to size 4, then
        // applyOutputCap drains to size 2 — drops A AND B (oldest
        // two) until the candidate is at the new cap. Survivors are
        // C=3, D=4.
        b.forceAdd(TEST_D, 4);

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        ItemId idC = idFor(TEST_C);
        ItemId idD = idFor(TEST_D);
        assertAll(
            () -> assertEquals(2, b.outputLedger().size(),
                "post-reset cap=2 with the 4th add: ledger drains to 2 — the new cap, "
                    + "not the prior cap=3, sized the drop. The drop loop fires "
                    + "iteratively (4 -> 3 -> 2), not just once"),
            () -> assertEquals(0, b.outputLedger().get(idA),
                "A is FIFO-dropped on the first iteration — it was the oldest entry"),
            () -> assertEquals(0, b.outputLedger().get(idB),
                "B is FIFO-dropped on the second iteration — oldest after A went"),
            () -> assertEquals(3, b.outputLedger().get(idC),
                "C is the post-reset survivor — kept in place, full quantity"),
            () -> assertEquals(4, b.outputLedger().get(idD),
                "D is the newest — kept in place, full quantity"),
            () -> assertFalse(b.getStockedItems().contains(TEST_A),
                "A is gone from the MC mirror — the post-reset FIFO drop fired "
                    + "the mirrorFifoDropToStock hook"),
            () -> assertFalse(b.getStockedItems().contains(TEST_B),
                "B is gone from the MC mirror — the second FIFO drop fired too"),
            () -> assertTrue(b.getStockedItems().contains(TEST_C)
                    && b.getStockedItems().contains(TEST_D),
                "C and D are on the MC mirror — the FIFO loop stopped at the new cap")
        );
    }

    // --------------------------------------------------------------------
    // Edge 3 — Mixed add + drain. forceAdd grows both sides; drain
    // shrinks both sides. Per-item quantities match at every step on
    // the partial-drain path. The full-drain path leaves a zero-keyed
    // entry on the MC mirror (the FIFO-drop hook calls stock.remove,
    // drain calls stock.put — see PlacedBuilding.drain); the ledger
    // drops the zero entry per its sparse discipline. This is a known
    // lockstep edge, not a bug — pin it so a future sparse-mirror carve
    // can replace it.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("mixed forceAdd + drain — partial drain keeps quantities in lockstep on both sides")
    void mixedAddAndDrainKeepsQuantitiesInLockstepOnPartialDrain() {
        BuildingOutputCap.setCurrent(new BuildingOutputCap(4));
        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 5);   // [A=5]
        b.forceAdd(TEST_B, 7);   // [A=5, B=7]
        b.drain(TEST_A, 3);      // [A=2, B=7]   — partial drain
        b.forceAdd(TEST_C, 1);   // [A=2, B=7, C=1]
        b.drain(TEST_B, 4);      // [A=2, B=3, C=1]   — partial drain
        b.forceAdd(TEST_D, 9);   // [A=2, B=3, C=1, D=9]

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        ItemId idC = idFor(TEST_C);
        ItemId idD = idFor(TEST_D);
        assertAll(
            () -> assertEquals(4, b.outputLedger().size(),
                "after the mix: ledger carries A, B, C, D — no FIFO drop fired"),
            () -> assertEquals(2, b.outputLedger().get(idA),
                "A: 5 (forceAdd) - 3 (partial drain) = 2 on the SoT ledger"),
            () -> assertEquals(3, b.outputLedger().get(idB),
                "B: 7 (forceAdd) - 4 (partial drain) = 3 on the SoT ledger"),
            () -> assertEquals(1, b.outputLedger().get(idC),
                "C: 1 (forceAdd) = 1 on the SoT ledger"),
            () -> assertEquals(9, b.outputLedger().get(idD),
                "D: 9 (forceAdd) = 9 on the SoT ledger"),
            () -> assertEquals(Integer.valueOf(2), b.getStock(TEST_A),
                "MC mirror matches the ledger for A on the partial-drain path"),
            () -> assertEquals(Integer.valueOf(3), b.getStock(TEST_B),
                "MC mirror matches the ledger for B on the partial-drain path"),
            () -> assertEquals(Integer.valueOf(1), b.getStock(TEST_C),
                "MC mirror matches the ledger for C — forceAdd only"),
            () -> assertEquals(Integer.valueOf(9), b.getStock(TEST_D),
                "MC mirror matches the ledger for D — forceAdd only"),
            () -> assertEquals(4, b.getStockedItems().size(),
                "all four items are on the MC mirror — neither drain dropped the key")
        );
    }

    @Test
    @DisplayName("full drain — SoT ledger drops the zero entry; MC mirror keeps the key at 0 (documented lockstep edge)")
    void fullDrainLeavesZeroKeyOnMirrorButDropsLedgerEntry() {
        // The full-drain edge: drain(A, available) takes the full
        // quantity, outputLedger.drop() removes the zero entry per its
        // sparse discipline, but mirrorFifoDropToStock is the FIFO-drop
        // hook (not the drain path) — drain's `stock.put(item,
        // available - taken)` leaves the key on the mirror at value 0.
        // This test pins that behavior; a future carve that wants
        // sparse mirror keys on the drain path too (mirror.put → remove
        // when next-quantity is 0) will replace this with a tighter
        // assertion.
        BuildingOutputCap.setCurrent(new BuildingOutputCap(4));
        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 5);   // [A=5]
        b.forceAdd(TEST_B, 7);   // [A=5, B=7]
        b.drain(TEST_A, 5);      // full drain: ledger drops A, mirror keeps {A=0, B=7}

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        assertAll(
            () -> assertEquals(1, b.outputLedger().size(),
                "SoT ledger has only B — full drain drops the zero entry per "
                    + "StockLedger's sparse discipline (setInternal removes on qty==0)"),
            () -> assertEquals(0, b.outputLedger().get(idA),
                "A's ledger entry is gone — sparse discipline preserved"),
            () -> assertEquals(7, b.outputLedger().get(idB),
                "B is untouched by the drain"),
            () -> assertEquals(Integer.valueOf(0), b.getStock(TEST_A),
                "MC mirror retains A at value 0 — drain uses stock.put, not "
                    + "stock.remove; the FIFO-drop hook is a different code path"),
            () -> assertEquals(Integer.valueOf(7), b.getStock(TEST_B),
                "MC mirror matches the ledger for B"),
            () -> assertTrue(b.getStockedItems().contains(TEST_A),
                "the documented lockstep edge: full-drain leaves A in the "
                    + "MC mirror's keySet at value 0 — the FIFO-drop hook "
                    + "(forceAdd overflow) would have called stock.remove, "
                    + "but drain does not"),
            () -> assertEquals(2, b.getStockedItems().size(),
                "the mirror carries both keys — the lockstep edge lives in "
                    + "keySet membership, not in per-item quantities")
        );
    }
}
