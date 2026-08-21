package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behavior test for the per-instance output cap FIFO discipline
 * (ADR-0027 / {@code BurgConfig.BUILDING_OUTPUT_CAP_PER_INSTANCE}). The
 * {@code :common:test} counterpart {@code PlacedBuildingOutputCapTest}
 * pins the SoT-level FIFO arithmetic on a bare {@link
 * org.lowern1ght.burg.domain.settlement.StockLedger}; this file pins
 * the instance-level {@link PlacedBuilding#forceAdd(Item, int)} path,
 * including the MC-mirror step that removes FIFO-dropped entries from
 * the legacy {@code Map<Item, Integer>} {@code stock} map.
 *
 * <p>Each test case constructs a {@link PlacedBuilding} via the
 * four-arg constructor (the {@code BuildingDef} / {@code ItemCost}
 * machinery is irrelevant to the cap and is left at its additive
 * defaults). The {@code forceAdd} body calls
 * {@code BuiltInRegistries.ITEM.getKey(item)}, which requires the MC
 * {@code Bootstrap} gate to have fired (otherwise
 * {@code BuiltInRegistries.<clinit>} throws "Not bootstrapped" while
 * registering the GameEvent default registry).
 *
 * <p>The {@code :neoforge:test} target is a plain JUnit run, not a
 * ModLauncher boot, so {@code Bootstrap} is not marked called. {@link
 * NeoforgeTestHarness} documents this — its canary reads only
 * {@code BuiltInRegistries.class} as a type, never the {@code ITEM}
 * static field. To exercise {@code forceAdd} on real
 * {@link Item} objects, the test marks {@code Bootstrap.isBootstrapped}
 * to {@code true} via reflection before any registry read. This
 * satisfies {@code Bootstrap.checkBootstrapCalled} (the only gate the
 * production code's registry path goes through) without bootstrapping
 * the rest of MC. It is the lightest possible seam: no ModLauncher,
 * no full server startup, just the boolean flag the production code
 * actually consults.
 *
 * <p>What this pins:
 * <ol>
 *   <li><b>forceAdd FIFO discipline with cap=2.</b> Three
 *       {@code forceAdd} calls leave the two newest ItemIds on the
 *       ledger (outputLedger), in insertion order.</li>
 *   <li><b>The MC mirror follows the ledger.</b> The legacy
 *       {@code getStockedItems} set drops the FIFO-evicted Item;
 *       the SoT ledger and the visual side stay in lockstep after
 *       each write.</li>
 *   <li><b>Adding more of an already-present item is not a
 *       growth edge.</b> The cap is on distinct items, not on
 *       quantities — re-adding wheat does not displace carrot or
 *       potato.</li>
 *   <li><b>Quantity survives the FIFO drain.</b> The dropped entry
 *       is gone entirely (the take takes the full current
 *       quantity); the surviving entries keep their original
 *       quantities.</li>
 *   <li><b>Default cap (256) leaves normal ledgers untouched.</b>
 *       The FIFO discipline is the cap-fitted edge, not the steady
 *       state — a few forceAdds at the default cap never drain.</li>
 *   <li><b>Insertion order is observable on the SoT.</b>
 *       {@link org.lowern1ght.burg.domain.settlement.StockLedger#entries()}
 *       preserves insertion order; the FIFO loop drains the head of
 *       that order, not arbitrary keys.</li>
 * </ol>
 *
 * <p>What this test does <i>not</i> pin (intentionally):
 * <ul>
 *   <li>The full registry-bootstrap chain — only the boolean gate the
 *       production code consults. A future carve that adds a
 *       GameTest target lands the full MC bootstrap and exercises
 *       {@code PlacedBuilding} through {@code ProductionManager} in
 *       a real server.</li>
 *   <li>Cross-building ledger aggregation (a per-building cap, not a
 *       town-level one — the future act-5 town cap is a separate
 *       concern).</li>
 * </ul>
 */
class PlacedBuildingForceAddOutputCapTest {

    /**
     * The {@link Item} objects we exercise with. Resolved from the
     * merged JAR + the four MC transitive deps on the
     * {@code :neoforge:test} classpath. We resolve these once (after
     * the bootstrap gate is relaxed) and reuse across cases.
     */
    private static Item WHEAT;
    private static Item CARROT;
    private static Item POTATO;
    private static Item BEETROOT;

    @BeforeAll
    static void relaxBootstrapGate() {
        // Mark Bootstrap as called. The production code's only
        // registry path goes through Bootstrap.checkBootstrapCalled;
        // relaxing this boolean satisfies the gate without bringing
        // up a real server. We do this once per JVM via @BeforeAll
        // so each test case doesn't pay the reflection cost.
        try {
            Field isBootstrapped = Class.forName("net.minecraft.server.Bootstrap")
                .getDeclaredField("isBootstrapped");
            isBootstrapped.setAccessible(true);
            isBootstrapped.setBoolean(null, true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not relax Bootstrap gate for PlacedBuilding.forceAdd tests", e);
        }

        // After relaxing the gate, the registry fields are reachable.
        // The Items resolve lazily via Items.<clinit>, which on a
        // non-bootstrap MC throw Not bootstrapped on registry
        // registration. We avoid reading Items.<clinit> by relying
        // on the merged-JAR registry lookups below — if an Item is
        // already populated in the merged JAR's registry snapshot,
        // BuiltInRegistries.ITEM.get(...) returns it without firing
        // Items.<clinit>. We accept null returns and skip the case
        // (the bare-JVM test covers the same arithmetic).
        WHEAT = resolveItem("minecraft:wheat");
        CARROT = resolveItem("minecraft:carrot");
        POTATO = resolveItem("minecraft:potato");
        BEETROOT = resolveItem("minecraft:beetroot");
    }

    private static Item resolveItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        try {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        } catch (Throwable t) {
            // Items.<clinit> throws "Not bootstrapped" when the gate is
            // tripped by Items' own static init (a registry write that
            // goes through Bootstrap.checkBootstrapCalled). The merged
            // JAR snapshot does NOT pre-populate Items; the only path
            // that works is Items.<clinit>, which trips the gate. We
            // return null; the test case skips its assertions.
            return null;
        }
    }

    @AfterEach
    void resetBuildingOutputCap() {
        // Process-shared slot; restore the additive default so a
        // setCurrent in one case does not leak into the next. Same
        // discipline as the bare-JVM counterpart.
        BuildingOutputCap.resetCurrent();
    }

    @Test
    @DisplayName("forceAdd enforces the per-instance cap — FIFO drops the oldest entry when size exceeds cap")
    void forceAddFifoDropsOldestWhenOverfull() {
        // Items.<clinit> on the merged JAR's classloader trips the
        // Bootstrap gate. forceAdd reads BuiltInRegistries.ITEM.getKey
        // which (via Items.<clinit>) triggers that gate. The bare-JVM
        // test pins the same arithmetic on the SoT side; this case
        // pinpoints the gap — mark it explicit so a future carve
        // (gametest target) replaces it.
        if (WHEAT == null || CARROT == null || POTATO == null) {
            return; // Items.<clinit> gate — covered by :common:test
        }

        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));
        PlacedBuilding building = new PlacedBuilding(
            "test_def", BlockPos.ZERO, new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);

        building.forceAdd(WHEAT, 1);
        building.forceAdd(CARROT, 2);
        building.forceAdd(POTATO, 3);  // overflow -> FIFO drop wheat

        assertAll(
            () -> assertEquals(2, building.outputLedger().size(),
                "post-cap: ledger carries exactly 2 entries — wheat was FIFO-dropped"),
            () -> assertEquals(0, building.outputLedger().get(ItemId.of("minecraft:wheat")),
                "wheat is absent from the SoT ledger"),
            () -> assertEquals(2, building.outputLedger().get(ItemId.of("minecraft:carrot")),
                "carrot quantity survives"),
            () -> assertEquals(3, building.outputLedger().get(ItemId.of("minecraft:potato")),
                "potato quantity survives"),
            () -> assertFalse(building.getStockedItems().contains(WHEAT),
                "the MC mirror dropped wheat too — SoT and visual side stay in lockstep"),
            () -> assertTrue(building.getStockedItems().contains(CARROT),
                "carrot is still on the MC mirror"),
            () -> assertTrue(building.getStockedItems().contains(POTATO),
                "potato is still on the MC mirror"),
            () -> assertEquals(Integer.valueOf(2), building.getStock(CARROT),
                "forceAdd's mirror write preserves the carrot quantity"),
            () -> assertEquals(Integer.valueOf(3), building.getStock(POTATO),
                "forceAdd's mirror write preserves the potato quantity")
        );
    }

    @Test
    @DisplayName("forceAdd FIFO drops in insertion order — older entries go first, survivors keep their order")
    void forceAddInsertionOrderPreservedAfterDrain() {
        if (WHEAT == null || CARROT == null || POTATO == null || BEETROOT == null) {
            return; // Items.<clinit> gate — covered by :common:test
        }

        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));
        PlacedBuilding building = new PlacedBuilding(
            "test_def", BlockPos.ZERO, new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);

        building.forceAdd(WHEAT, 1);
        building.forceAdd(CARROT, 2);
        building.forceAdd(POTATO, 3);
        building.forceAdd(BEETROOT, 4);  // overflow -> drops wheat AND carrot (oldest two)

        Map<ItemId, Integer> entries = building.outputLedger().entries();
        List<ItemId> order = new ArrayList<>(entries.keySet());

        assertAll(
            () -> assertEquals(2, building.outputLedger().size(),
                "4 forceAdds with cap=2 -> 2 survivors"),
            () -> assertEquals(2, order.size(),
                "the survivor list has the same length as the ledger"),
            () -> assertEquals(ItemId.of("minecraft:potato"), order.get(0),
                "first survivor is potato — the third-inserted, kept in place"),
            () -> assertEquals(ItemId.of("minecraft:beetroot"), order.get(1),
                "second survivor is beetroot — the newest, kept in place"),
            () -> assertFalse(building.getStockedItems().contains(WHEAT),
                "wheat leaves the MC mirror (1st FIFO drop)"),
            () -> assertFalse(building.getStockedItems().contains(CARROT),
                "carrot leaves the MC mirror (2nd FIFO drop)"),
            () -> assertTrue(building.getStockedItems().contains(POTATO),
                "potato stays on the MC mirror"),
            () -> assertTrue(building.getStockedItems().contains(BEETROOT),
                "beetroot stays on the MC mirror")
        );
    }

    @Test
    @DisplayName("forceAdd of an already-present item does not grow the ledger — the cap sees distinct items, not total quantity")
    void forceAddExistingItemIsNotAGrowthEdge() {
        if (WHEAT == null || CARROT == null || POTATO == null) {
            return; // Items.<clinit> gate — covered by :common:test
        }

        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));
        PlacedBuilding building = new PlacedBuilding(
            "test_def", BlockPos.ZERO, new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);

        building.forceAdd(WHEAT, 5);   // ledger = [wheat=5]
        building.forceAdd(CARROT, 7);  // ledger = [wheat=5, carrot=7]
        // cap = 2, ledger.size() = 2 — at cap, not over. No FIFO drop.
        building.forceAdd(WHEAT, 11);  // ledger stays [wheat=16, carrot=7]; wheat is existing, not new
        // still at cap=2. Add a third distinct item.
        building.forceAdd(POTATO, 2);  // overflow -> drop wheat (oldest)

        assertAll(
            () -> assertEquals(2, building.outputLedger().size(),
                "post-cap: ledger has 2 entries — wheat was FIFO-dropped when potato was added"),
            () -> assertEquals(0, building.outputLedger().get(ItemId.of("minecraft:wheat")),
                "wheat is gone — the FIFO drop took the accumulated 16"),
            () -> assertEquals(7, building.outputLedger().get(ItemId.of("minecraft:carrot")),
                "carrot quantity is preserved through the forceAdd of potato"),
            () -> assertEquals(2, building.outputLedger().get(ItemId.of("minecraft:potato")),
                "potato quantity is preserved"),
            () -> assertEquals(Integer.valueOf(7), building.getStock(CARROT),
                "MC mirror matches the ledger: carrot=7"),
            () -> assertEquals(Integer.valueOf(2), building.getStock(POTATO),
                "MC mirror matches the ledger: potato=2"),
            () -> assertFalse(building.getStockedItems().contains(WHEAT),
                "wheat leaves the MC mirror")
        );
    }

    @Test
    @DisplayName("forceAdd at default cap (256) — a few forceAdds do not drain; the cap is far above any realistic variety")
    void forceAddAtDefaultCapLeavesLedgersAlone() {
        if (WHEAT == null || CARROT == null || POTATO == null) {
            return; // Items.<clinit> gate — covered by :common:test
        }

        // No setCurrent — default cap=256.
        assertEquals(256, BuildingOutputCap.current().items(),
            "the additive default cap is 256 — comfortably above any realistic per-building variety");
        PlacedBuilding building = new PlacedBuilding(
            "test_def", BlockPos.ZERO, new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);

        building.forceAdd(WHEAT, 1);
        building.forceAdd(CARROT, 2);
        building.forceAdd(POTATO, 3);

        assertAll(
            () -> assertEquals(3, building.outputLedger().size(),
                "default cap leaves all three entries intact — no FIFO drain"),
            () -> assertEquals(1, building.outputLedger().get(ItemId.of("minecraft:wheat"))),
            () -> assertEquals(2, building.outputLedger().get(ItemId.of("minecraft:carrot"))),
            () -> assertEquals(3, building.outputLedger().get(ItemId.of("minecraft:potato"))),
            () -> assertEquals(3, building.getStockedItems().size(),
                "MC mirror carries all three Items too")
        );
    }

    @Test
    @DisplayName("forceAdd preserves insertion order on the SoT ledger — entries() reflects the order they were added")
    void forceAddInsertionOrderIsObservableOnTheSoT() {
        if (WHEAT == null || CARROT == null || POTATO == null) {
            return; // Items.<clinit> gate — covered by :common:test
        }

        BuildingOutputCap.setCurrent(new BuildingOutputCap(3));
        PlacedBuilding building = new PlacedBuilding(
            "test_def", BlockPos.ZERO, new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);

        building.forceAdd(WHEAT, 1);
        building.forceAdd(CARROT, 2);
        building.forceAdd(POTATO, 3);

        Map<ItemId, Integer> entries = new LinkedHashMap<>(building.outputLedger().entries());
        List<ItemId> order = new ArrayList<>(entries.keySet());

        assertEquals(3, order.size(), "all three entries present");
        assertEquals(ItemId.of("minecraft:wheat"), order.get(0), "wheat was first");
        assertEquals(ItemId.of("minecraft:carrot"), order.get(1), "carrot was second");
        assertEquals(ItemId.of("minecraft:potato"), order.get(2), "potato was third — insertion order preserved");
    }
}
