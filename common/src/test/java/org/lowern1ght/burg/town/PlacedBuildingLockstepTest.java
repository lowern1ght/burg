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
 * Lockstep pin for {@link PlacedBuilding#forceAdd(Item, int)}: every call
 * updates both the per-instance {@link org.lowern1ght.burg.domain.settlement.StockLedger}
 * source of truth (the {@code outputLedger} field, PR #50's carve) and the
 * legacy {@code Map<Item, Integer> stock} MC mirror with the same quantity.
 * The {@code :common:test} counterpart of {@code PlacedBuildingOutputSotTest}
 * (which pins the SoT shape via reflection only) and of
 * {@code PlacedBuildingForceAddOutputCapTest} in {@code :neoforge:test}
 * (which pins the FIFO discipline with real MC Items, but only after the
 * bare-JVM registry-bootstrap gate is relaxed — that test has the same
 * Bootstrap-relax + Item-register seam this file uses, with a different
 * focus).
 *
 * <p><b>The deferred gap this test closes.</b> PR #50's commit message
 * flagged: <i>"ledger + MC map могут расходиться при ошибках в
 * forceAdd"</i> — the discipline that {@code forceAdd} keeps the SoT
 * ledger and the MC mirror in lockstep was deferred to a future carve.
 * PR #46 introduced the dual-write (SoT first, MC map second), and the
 * FIFO cap (PR {@code feature/cap-fifo} / ADR-0027) added a mirror step
 * on drop. The full lockstep contract — both sides agree at every
 * mutation edge — was not pinned bare-JVM.
 *
 * <p><b>How this test exercises it without a real Minecraft world.</b>
 * The {@code :common:test} target carries the ModDev merged JAR plus
 * the four critical MC transitives Town pulls in via its static init
 * (SLF4J, brigadier, datafixerupper, authlib — see
 * {@code common/build.gradle} §"Plain JVM tests, no Minecraft"), so
 * {@code new PlacedBuilding(...)} works at this target. The barrier
 * is {@code Bootstrap.checkBootstrapCalled}: every
 * {@link net.minecraft.core.registries.BuiltInRegistries} read routes
 * through it, and the JUnit JVM (no ModLauncher boot) has not run
 * Bootstrap. The seam that unlocks the registry path is the same one
 * the existing MC-aware tests use:
 *
 * <ol>
 *   <li>Reflectively flip {@code Bootstrap.isBootstrapped} to
 *       {@code true} <i>before</i> any registry read — that satisfies
 *       the only gate the production code's registry path goes
 *       through. {@code Items.<clinit>} still trips downstream on
 *       {@code DataFixers.<clinit>} ("Game version not set"), but we
 *       never read {@code Items.<clinit>} directly — every {@link Item}
 *       in this test is a fresh {@code new Item(Properties)} that
 *       never enters the Items constants.</li>
 *   <li>Construct two fresh {@link Item} instances via
 *       {@code Item(Properties)}. Their {@code <init>} calls
 *       {@code BuiltInRegistries.ITEM.createIntrusiveHolder(this)},
 *       which goes through Bootstrap — relaxed above, so it
 *       succeeds.</li>
 *   <li>Register each constructed Item into
 *       {@link BuiltInRegistries.ITEM} via
 *       {@link Registry#register(net.minecraft.core.Registry, ResourceLocation, Object)}
 *       with a private namespace
 *       ({@code placelockstep:test_a}, {@code placelockstep:test_b},
 *       {@code placelockstep:test_c}) so the {@code ItemId} keys land
 *       on the {@code outputLedger} with a deterministic, test-only
 *       value. Real MC namespaces are reserved for the live
 *       registry, so we do not touch them — same discipline the
 *       {@code :neoforge:test} writers use when they need a
 *       one-off Item.</li>
 * </ol>
 *
 * <p>With the registry populated, {@code forceAdd} reaches
 * {@code BuiltInRegistries.ITEM.getKey(item)} (which looks the
 * registered key up directly — no Items.<clinit>) and the
 * {@code ItemId.parseOrEmpty} call derives the deterministic test key
 * on the SoT ledger. The MC {@code stock} mirror uses the Item
 * object as key; both sides agree.
 *
 * <p><b>What this pins.</b>
 * <ol>
 *   <li><b>Single {@code forceAdd}.</b> One call writes the same
 *       quantity to {@code outputLedger} (under the ItemId key)
 *       and to {@code stock} (under the Item key).</li>
 *   <li><b>Accumulation across calls on the same item.</b> Two
 *       {@code forceAdd}s of the same Item sum to the same value
 *       on both sides; the ledger stays sparse (one entry, the
 *       running total) and the stock map stays one-entry too.</li>
 *   <li><b>Two distinct items.</b> {@code forceAdd} on a fresh
 *       ItemId grows both sides by one entry each; per-item
 *       quantities match between ledger and stock.</li>
 *   <li><b>FIFO drain under the cap mirrors on both sides.</b> With
 *       the per-instance cap set to a small value, the oldest
 *       ItemId is dropped from the SoT ledger and the corresponding
 *       Item is removed from the MC {@code stock} map; the
 *       survivor's quantities are identical on both sides. This is
 *       the discipline {@code PlacedBuildingOutputCapTest}'s static
 *       helper {@code applyOutputCap} drives, pinned here on the
 *       live {@code forceAdd} edge (which {@code :common:test}'s
 *       static-helper-only counterpart does not reach).</li>
 *   <li><b>Quantity survives the FIFO drain.</b> The dropped
 *       entry's full quantity is taken; the survivors carry
 *       through unchanged on both sides.</li>
 * </ol>
 *
 * <p>What this test does <i>not</i> pin (intentional residuals):
 * <ul>
 *   <li>The full vanilla registry-bootstrap chain — the
 *       {@code Bootstrap} relax only opens the gate the production
 *       code's registry path goes through; the live server's
 *       GameEvent dispatch + DataFixers chain runs in
 *       {@code PlacedBuildingLiveTest} (the {@code :neoforge:gametest}
 *       carve). That file exercises {@code forceAdd} on real
 *       {@code Items.WHEAT}, {@code Items.CARROT},
 *       {@code Items.POTATO}; this file exercises the same contract
 *       on constructed-and-registered Items so the lockstep pin is
 *       testable on the plain JUnit target.</li>
 *   <li>Insertion order on the MC mirror — {@code PlacedBuilding.stock}
 *       is a {@code HashMap}, not a {@code LinkedHashMap}; the
 *       MC-side order is non-deterministic. The SoT-side
 *       {@code StockLedger.entries()} IS insertion-ordered (the
 *       FIFO discipline relies on it); the SoT-ordering is pinned
 *       in {@code PlacedBuildingOutputCapTest}.</li>
 * </ul>
 */
class PlacedBuildingLockstepTest {

    /**
     * The three constructed Items this test exercises. The keys
     * (private {@code placelockstep:test_a/b/c} namespace) keep the
     * {@code ItemId} keys off the vanilla registry while still giving
     * every {@link Item} a deterministic lookup identity on the SoT
     * ledger. Resolved once after the {@code Bootstrap} gate is relaxed.
     */
    private static Item TEST_A;
    private static Item TEST_B;
    private static Item TEST_C;

    /** Resource keys for the test items — used to derive the {@code ItemId} keys on the ledger. */
    private static final ResourceLocation KEY_A =
        ResourceLocation.fromNamespaceAndPath("placelockstep", "test_a");
    private static final ResourceLocation KEY_B =
        ResourceLocation.fromNamespaceAndPath("placelockstep", "test_b");
    private static final ResourceLocation KEY_C =
        ResourceLocation.fromNamespaceAndPath("placelockstep", "test_c");

    @BeforeAll
    static void bootstrapAndRegisterItems() throws Exception {
        // Mark Bootstrap as called before any registry read. The
        // production code's only registry path goes through
        // Bootstrap.checkBootstrapCalled; relaxing this boolean
        // satisfies the gate without bringing up a real server.
        // Items.<clinit> still trips downstream ("Game version not
        // set" via DataFixers.<clinit>), but we never read it — every
        // Item here is a fresh `new Item(Properties)` that bypasses
        // the Items constants. Same seam the :neoforge:test
        // PlacedBuildingForceAddOutputCapTest uses.
        Field isBootstrapped = Class.forName("net.minecraft.server.Bootstrap")
            .getDeclaredField("isBootstrapped");
        isBootstrapped.setAccessible(true);
        isBootstrapped.setBoolean(null, true);

        // Construct the three test Items. Item.<init> calls
        // BuiltInRegistries.ITEM.createIntrusiveHolder(this) — sees
        // the relaxed Bootstrap gate above, succeeds.
        Class<?> propsClass = Class.forName("net.minecraft.world.item.Item$Properties");
        TEST_A = new Item((Item.Properties) propsClass.getDeclaredConstructor().newInstance());
        TEST_B = new Item((Item.Properties) propsClass.getDeclaredConstructor().newInstance());
        TEST_C = new Item((Item.Properties) propsClass.getDeclaredConstructor().newInstance());

        // Register each Item into BuiltInRegistries.ITEM with a
        // private namespace key. Registry.register is the static
        // helper that delegates to WritableRegistry.register; it
        // goes through Bootstrap.checkBootstrapCalled (relaxed)
        // and through validateWrite (the registry is not frozen at
        // this stage of the JUnit JVM — neither Items.<clinit> nor
        // freeze() has fired).
        Registry.register(BuiltInRegistries.ITEM, KEY_A, TEST_A);
        Registry.register(BuiltInRegistries.ITEM, KEY_B, TEST_B);
        Registry.register(BuiltInRegistries.ITEM, KEY_C, TEST_C);
    }

    @AfterEach
    void resetBuildingOutputCap() {
        // Process-shared slot — restore the additive default so a
        // setCurrent in one case does not leak into the next. Same
        // discipline as PlacedBuildingOutputCapTest +
        // PlacedBuildingForceAddOutputCapTest + RaidConfigTest +
        // RaidManagerTest.
        BuildingOutputCap.resetCurrent();
    }

    /**
     * Helper: build a fresh {@link PlacedBuilding} at the world origin
     * with a 1×1×1 bounding box. The {@code BuildingDef} /
     * {@code ItemCost} machinery is irrelevant to the cap and the
     * lockstep contract and is left at its additive defaults.
     */
    private static PlacedBuilding freshBuilding() {
        return new PlacedBuilding(
            "lockstep_test", BlockPos.ZERO,
            new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);
    }

    /**
     * Helper: derive the {@link ItemId} key for a registered Item,
     * using the registry key BuiltInRegistries.ITEM.getKey returns.
     * This is the same key forceAdd's body builds via
     * ItemId.parseOrEmpty(BuiltInRegistries.ITEM.getKey(item).toString()),
     * so the assertion is on the exact key the production code uses.
     */
    private static ItemId idFor(Item item) {
        return ItemId.of(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    // --------------------------------------------------------------------
    // Lockstep — single forceAdd writes the same quantity to both sides.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("forceAdd(item, 5) — outputLedger[id] and stock[item] both carry 5 (single-write lockstep)")
    void singleForceAddUpdatesBothSides() {
        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 5);

        ItemId idA = idFor(TEST_A);
        assertAll(
            () -> assertEquals(5, b.outputLedger().get(idA),
                "after forceAdd(A, 5), the SoT ledger carries 5 under A's ItemId"),
            () -> assertEquals(1, b.outputLedger().size(),
                "the SoT ledger has exactly one entry — the sparse discipline holds"),
            () -> assertEquals(Integer.valueOf(5), b.getStock(TEST_A),
                "the MC mirror carries 5 under A — SoT and visual side stay in lockstep"),
            () -> assertTrue(b.getStockedItems().contains(TEST_A),
                "A is on the MC mirror (the FIFO discipline will rely on getStockedItems)")
        );
    }

    // --------------------------------------------------------------------
    // Lockstep — repeated forceAdd on the same Item accumulates on
    // both sides; sparse discipline preserved.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("two forceAdd(A, 5) + (A, 3) — both sides accumulate to 8; ledger stays sparse (one entry)")
    void accumulationOnSameItemUpdatesBothSides() {
        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 5);
        b.forceAdd(TEST_A, 3);

        ItemId idA = idFor(TEST_A);
        assertAll(
            () -> assertEquals(8, b.outputLedger().get(idA),
                "two adds on A sum on the SoT ledger — the 5+3 accumulation the discipline relies on"),
            () -> assertEquals(1, b.outputLedger().size(),
                "the SoT ledger stays sparse — one entry, the sum of both adds"),
            () -> assertEquals(Integer.valueOf(8), b.getStock(TEST_A),
                "the MC mirror accumulates to 8 — SoT and visual side agree on the total"),
            () -> assertEquals(1, b.getStockedItems().size(),
                "the MC mirror still has exactly one entry — adding more of an existing item is not a growth edge")
        );
    }

    // --------------------------------------------------------------------
    // Lockstep — distinct Items grow both sides by one entry each.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("forceAdd(A, 5) + forceAdd(A, 3) + forceAdd(B, 10) — ledger and stock each have two entries; per-item quantities match")
    void distinctItemsGrowBothSides() {
        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 5);
        b.forceAdd(TEST_A, 3);
        b.forceAdd(TEST_B, 10);

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        assertAll(
            () -> assertEquals(2, b.outputLedger().size(),
                "two distinct ItemIds — A and B — on the SoT ledger"),
            () -> assertEquals(8, b.outputLedger().get(idA),
                "A's running total on the SoT ledger: 5+3 = 8"),
            () -> assertEquals(10, b.outputLedger().get(idB),
                "B's running total on the SoT ledger: 10"),
            () -> assertEquals(2, b.getStockedItems().size(),
                "the MC mirror also has two entries — A and B"),
            () -> assertEquals(Integer.valueOf(8), b.getStock(TEST_A),
                "A's MC mirror matches the ledger: 8"),
            () -> assertEquals(Integer.valueOf(10), b.getStock(TEST_B),
                "B's MC mirror matches the ledger: 10"),
            () -> assertTrue(b.getStockedItems().contains(TEST_A)
                    && b.getStockedItems().contains(TEST_B),
                "both Items are on the MC mirror")
        );
    }

    // --------------------------------------------------------------------
    // Lockstep — FIFO drain under the per-instance cap drops the same
    // entry on both sides; survivors carry their quantities.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("cap=2 + three forceAdds — FIFO drops the oldest ItemId on the ledger AND the oldest Item on the MC mirror")
    void fifoCapDrainDropsBothSides() {
        // Drive the per-instance cap to a small value so the FIFO
        // discipline fires on the third add. The default cap is 256
        // — a three-add fixture is well under that and would never
        // drop without the override.
        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));

        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 1);
        b.forceAdd(TEST_B, 2);
        b.forceAdd(TEST_C, 3);   // overflow → FIFO drop A

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        ItemId idC = idFor(TEST_C);
        assertAll(
            () -> assertEquals(2, b.outputLedger().size(),
                "post-cap: SoT ledger has exactly 2 entries — A was FIFO-dropped"),
            () -> assertEquals(0, b.outputLedger().get(idA),
                "A is gone from the SoT ledger (FIFO drop took the full quantity)"),
            () -> assertEquals(2, b.outputLedger().get(idB),
                "B survives the FIFO drain on the ledger with its original quantity"),
            () -> assertEquals(3, b.outputLedger().get(idC),
                "C is the newest — it stays on the ledger with its original quantity"),
            () -> assertFalse(b.getStockedItems().contains(TEST_A),
                "A is gone from the MC mirror — the mirrorFifoDropToStock hook removed it"),
            () -> assertTrue(b.getStockedItems().contains(TEST_B)
                    && b.getStockedItems().contains(TEST_C),
                "B and C survive on the MC mirror — the lockstep discipline is the FIFO drop"),
            () -> assertEquals(Integer.valueOf(2), b.getStock(TEST_B),
                "the MC mirror preserves B's quantity through the FIFO drop"),
            () -> assertEquals(Integer.valueOf(3), b.getStock(TEST_C),
                "the MC mirror preserves C's quantity through the FIFO drop")
        );
    }

    // --------------------------------------------------------------------
    // Lockstep — second FIFO drop (cap=2, four adds) drains the next
    // oldest on both sides; the FIFO discipline does not cross-pollute
    // the surviving entries.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("cap=2 + four forceAdds — FIFO drops the two oldest on both sides; the two survivors match")
    void fifoCapDoubleDrainDropsBothSides() {
        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));

        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 1);
        b.forceAdd(TEST_B, 2);
        b.forceAdd(TEST_C, 3);   // overflow → drop A
        b.forceAdd(TEST_A, 4);   // overflow → drop B (now oldest)
        // Survivors after the double drain: C=3, A's second add = +4

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        ItemId idC = idFor(TEST_C);
        assertAll(
            () -> assertEquals(2, b.outputLedger().size(),
                "post-double-drain: SoT ledger has 2 entries — A (second add) and C"),
            () -> assertEquals(4, b.outputLedger().get(idA),
                "A's second forceAdd survives with quantity 4 — the FIFO drop targeted B, not A"),
            () -> assertEquals(0, b.outputLedger().get(idB),
                "B is gone from the ledger — it was the oldest at the second FIFO drop"),
            () -> assertEquals(3, b.outputLedger().get(idC),
                "C survives unchanged through the second FIFO drop"),
            () -> assertFalse(b.getStockedItems().contains(TEST_B),
                "B is gone from the MC mirror — the mirror follows the ledger's FIFO"),
            () -> assertTrue(b.getStockedItems().contains(TEST_A),
                "A's second add is on the MC mirror"),
            () -> assertTrue(b.getStockedItems().contains(TEST_C),
                "C is on the MC mirror"),
            () -> assertEquals(Integer.valueOf(4), b.getStock(TEST_A),
                "the MC mirror preserves A's second quantity"),
            () -> assertEquals(Integer.valueOf(3), b.getStock(TEST_C),
                "the MC mirror preserves C's quantity")
        );
    }

    // --------------------------------------------------------------------
    // Lockstep — re-adding an already-present item is not a growth
    // edge; the cap sees distinct items, not total quantity. Both
    // sides stay in lockstep across the no-drop / drop edges.
    // --------------------------------------------------------------------

    @Test
    @DisplayName("cap=2 — re-adding an existing item is not a growth edge; lockstep holds across the no-drop and drop paths")
    void reAddingExistingItemStaysInLockstep() {
        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));

        PlacedBuilding b = freshBuilding();

        b.forceAdd(TEST_A, 5);   // ledger = [A=5]
        b.forceAdd(TEST_B, 7);   // ledger = [A=5, B=7]
        // cap=2 with 2 distinct items — at cap, not over.
        b.forceAdd(TEST_A, 11);  // A is existing — not a growth edge, no FIFO drop
        // still at cap. Add C — overflow → drop A (now oldest).
        b.forceAdd(TEST_C, 2);

        ItemId idA = idFor(TEST_A);
        ItemId idB = idFor(TEST_B);
        ItemId idC = idFor(TEST_C);
        assertAll(
            () -> assertEquals(2, b.outputLedger().size(),
                "post-cap: SoT ledger has 2 entries — A was FIFO-dropped when C was added"),
            () -> assertEquals(0, b.outputLedger().get(idA),
                "A's accumulated 16 was taken by the FIFO drop"),
            () -> assertEquals(7, b.outputLedger().get(idB),
                "B's quantity survives through the forceAdd of A's +11 and C's +2"),
            () -> assertEquals(2, b.outputLedger().get(idC),
                "C's quantity is preserved"),
            () -> assertFalse(b.getStockedItems().contains(TEST_A),
                "A is gone from the MC mirror"),
            () -> assertEquals(Integer.valueOf(7), b.getStock(TEST_B),
                "the MC mirror matches the ledger for B: 7"),
            () -> assertEquals(Integer.valueOf(2), b.getStock(TEST_C),
                "the MC mirror matches the ledger for C: 2")
        );
    }
}
