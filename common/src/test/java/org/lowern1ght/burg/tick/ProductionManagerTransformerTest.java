package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.settlement.TransformationRule;
import org.lowern1ght.burg.domain.settlement.TransformationRule.StockCost;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-JVM contract for the budget half of {@code ProductionManager.tickTransformer}.
 *
 * <p>The full transformer loop is MC-side (it consumes a {@code TownInventory},
 * reads {@code PlacedBuilding.stock}, and calls {@code building.forceAdd}), so
 * it cannot be tested without Minecraft. What <em>can</em> be tested bare-JVM
 * is the carved helper {@code ProductionManager.runTransformerBudget} — its
 * only inputs are {@link TransformationRule} lists and a {@link StockLedger}
 * budget, and its output is a package-private record
 * {@code ProductionManager.TransformerBudgetPass}.
 *
 * <p>This class pins the four invariants the wiring in
 * {@code tickTransformer} relies on:
 * <ol>
 *   <li>A single transformation with enough budget fires once
 *       ({@link #singleTransformWithEnoughBudget_applies}).</li>
 *   <li>A single transformation without enough budget is skipped — the
 *       pass returns an empty {@code appliedPerRule} map and an empty
 *       {@code consumedPerItem} map ({@link #singleTransformWithoutEnoughBudget_isSkipped}).</li>
 *   <li>Two transformations consuming the same input accumulate: the
 *       helper fires both across the multi-pass loop and the
 *       per-item consumed total sums them
 *       ({@link #twoTransformsConsumingSameInput_budgetAccumulates}).</li>
 *   <li>The legacy zero-qty output drop (already pinned in
 *       {@code StockLedgerMutationTest}) carries over: even though no
 *       rule here can yield a zero output by construction, an applied
 *       rule credits the helper's {@code appliedPerRule} with the rule
 *       itself (the {@link TransformationRule} record contract rejects
 *       zero output amounts at construction), so the wiring never
 *       drops a fired rule on the way out
 *       ({@link #appliedRuleIsAlwaysRecorded_neverDropsToZero}).</li>
 * </ol>
 *
 * <p>The output half of the transformer pass (writing back to
 * {@code PlacedBuilding.stock} and draining {@code TownInventory}) is
 * still MC-typed and is exercised in-game; this file owns the budget
 * arithmetic only.
 */
class ProductionManagerTransformerTest {

    private static final ItemId WHEAT = ItemId.of("minecraft:wheat");
    private static final ItemId COAL = ItemId.of("minecraft:coal");
    private static final ItemId OAK_LOG = ItemId.of("minecraft:oak_log");
    private static final ItemId FLOUR = ItemId.of("minecraft:flour");
    private static final ItemId STICK = ItemId.of("minecraft:stick");
    private static final ItemId TORCH = ItemId.of("minecraft:torch");

    private static TransformationRule rule(
        List<StockCost> inputs, ItemId output, int amount, int capacityItems) {
        return new TransformationRule(inputs, output, amount, capacityItems);
    }

    @Test
    @DisplayName("single transform with enough budget — fires greedily until the budget runs out")
    void singleTransformWithEnoughBudget_applies() {
        // Multi-pass loop: 4 wheat budget, 2 wheat per fire — the
        // helper fires twice (budget dies before the per-tick capacity
        // of 3 fires is reached). This is the same shape the legacy MC
        // loop had, only the arithmetic now lives on a StockLedger.
        TransformationRule wheatToFlour = rule(
            List.of(new StockCost(WHEAT, 2)), FLOUR, 1, 64);
        StockLedger budget = StockLedger.EMPTY.add(WHEAT, 4);

        ProductionManager.TransformerBudgetPass pass = ProductionManager.runTransformerBudget(
            List.of(wheatToFlour), budget, new int[]{3});

        assertAll(
            () -> assertEquals(2, pass.appliedPerRule().get(wheatToFlour),
                "two fires — the budget cap of 4 wheat died before the per-tick cap"),
            () -> assertEquals(4, pass.consumedPerItem().get(WHEAT),
                "two fires of 2 wheat = 4 total consumed"),
            () -> assertEquals(0, pass.nextBudget().get(WHEAT),
                "the budget ledger fully drained"),
            () -> assertEquals(2, pass.nextBudget().get(FLOUR),
                "two fires of 1 flour credit the budget ledger")
        );
    }

    @Test
    @DisplayName("single transform without enough budget — skipped, applied and consumed maps empty")
    void singleTransformWithoutEnoughBudget_isSkipped() {
        TransformationRule wheatToFlour = rule(
            List.of(new StockCost(WHEAT, 2)), FLOUR, 1, 64);
        // Only one wheat — the rule needs two.
        StockLedger budget = StockLedger.EMPTY.add(WHEAT, 1);

        ProductionManager.TransformerBudgetPass pass = ProductionManager.runTransformerBudget(
            List.of(wheatToFlour), budget, new int[]{64});

        assertAll(
            () -> assertTrue(pass.appliedPerRule().isEmpty(),
                "no fires were recorded"),
            () -> assertTrue(pass.consumedPerItem().isEmpty(),
                "no consumption happened"),
            () -> assertEquals(1, pass.nextBudget().get(WHEAT),
                "the budget is returned unchanged — failed-apply does not drain")
        );
    }

    @Test
    @DisplayName("two transforms consuming the same input — multi-pass fires both and consumed totals add")
    void twoTransformsConsumingSameInput_budgetAccumulates() {
        // The helper is a greedy multi-pass loop. Every pass tries every
        // rule in turn; a rule whose budget line is met fires. With 4 wheat
        // and a 2-wheat sticks rule plus a 1-wheat flour rule, the first
        // pass fires BOTH rules (sticks first, then flour on the surplus).
        // Wheat drops to 1. The second pass fires flour again (1>=1). The
        // third pass fires neither rule and the loop terminates. Sticks
        // fired once, flour fired twice, total wheat consumed = 4.
        TransformationRule wheatToSticks = rule(
            List.of(new StockCost(WHEAT, 2)), STICK, 4, 256);
        TransformationRule wheatToFlour = rule(
            List.of(new StockCost(WHEAT, 1)), FLOUR, 1, 64);
        StockLedger budget = StockLedger.EMPTY.add(WHEAT, 4);

        ProductionManager.TransformerBudgetPass pass = ProductionManager.runTransformerBudget(
            List.of(wheatToSticks, wheatToFlour), budget, new int[]{10, 10});

        assertAll(
            () -> assertEquals(1, pass.appliedPerRule().get(wheatToSticks),
                "sticks fire consumed 2 wheat and left the floor at 2"),
            () -> assertEquals(2, pass.appliedPerRule().get(wheatToFlour),
                "flour fires twice — once on the first pass surplus, once on the second"),
            () -> assertEquals(4, pass.consumedPerItem().get(WHEAT),
                "per-item consumed sums across all fires (2 sticks + 2 flour = 4)"),
            () -> assertEquals(0, pass.nextBudget().get(WHEAT),
                "the wheat budget is fully drained — every pass applied")
        );
    }

    @Test
    @DisplayName("remaining fires per rule caps the loop — even with infinite budget")
    void remainingFiresPerRule_capsTheLoop() {
        // Plenty of input, but `remaining` of 1 means a single fire.
        TransformationRule wheatToFlour = rule(
            List.of(new StockCost(WHEAT, 1)), FLOUR, 1, 64);
        StockLedger budget = StockLedger.EMPTY.add(WHEAT, 100);

        ProductionManager.TransformerBudgetPass pass = ProductionManager.runTransformerBudget(
            List.of(wheatToFlour), budget, new int[]{1});

        assertAll(
            () -> assertEquals(1, pass.appliedPerRule().get(wheatToFlour),
                "the per-tick capacity is the ceiling, not the budget"),
            () -> assertEquals(1, pass.consumedPerItem().get(WHEAT),
                "a single fire consumes one wheat"),
            () -> assertEquals(99, pass.nextBudget().get(WHEAT),
                "the surplus budget is preserved untouched")
        );
    }

    @Test
    @DisplayName("applies never drop on the way out — every fire credits the helper's map with the rule itself")
    void appliedRuleIsAlwaysRecorded_neverDropsToZero() {
        // The drop behaviour is already pinned in StockLedgerMutationTest
        // (zero-qty add returns the same ledger); here we confirm the
        // helper wires only positive fires. Because TransformationRule
        // rejects outputAmount <= 0 at construction, every fire credits a
        // non-zero count into appliedPerRule by design.
        TransformationRule wheatToFlour = rule(
            List.of(new StockCost(WHEAT, 1)), FLOUR, 1, 64);
        StockLedger budget = StockLedger.EMPTY.add(WHEAT, 3);

        ProductionManager.TransformerBudgetPass pass = ProductionManager.runTransformerBudget(
            List.of(wheatToFlour), budget, new int[]{5});

        assertAll(
            () -> assertFalse(pass.appliedPerRule().isEmpty(),
                "at least one fire recorded"),
            () -> assertTrue(pass.appliedPerRule().get(wheatToFlour) > 0,
                "every fired count is positive by rule construction"),
            () -> assertEquals(3, pass.appliedPerRule().get(wheatToFlour),
                "the loop fires until the budget is empty (one wheat per fire)")
        );
    }

    @Test
    @DisplayName("apply ignores sibling items — unrelated budget entries survive untouched")
    void applyLeavesSiblingItemsAlone() {
        // The budget has unrelated inputs (oak logs and coal) the rule
        // does not touch. The helper must leave those alone while it
        // greedily fires the wheat rule until the wheat budget is empty.
        TransformationRule wheatToFlour = rule(
            List.of(new StockCost(WHEAT, 1)), FLOUR, 1, 64);
        StockLedger budget = StockLedger.EMPTY
            .add(WHEAT, 3)
            .add(OAK_LOG, 7)
            .add(COAL, 9);

        ProductionManager.TransformerBudgetPass pass = ProductionManager.runTransformerBudget(
            List.of(wheatToFlour), budget, new int[]{10});

        assertAll(
            () -> assertEquals(3, budget.get(WHEAT),
                "caller's ledger is untouched"),
            () -> assertEquals(0, pass.nextBudget().get(WHEAT),
                "the wheat budget drained across three fires"),
            () -> assertEquals(7, pass.nextBudget().get(OAK_LOG),
                "untouched sibling is preserved"),
            () -> assertEquals(9, pass.nextBudget().get(COAL),
                "untouched sibling is preserved"),
            () -> assertEquals(3, pass.appliedPerRule().get(wheatToFlour),
                "three fires until the wheat budget dies"),
            () -> assertEquals(3, pass.consumedPerItem().get(WHEAT),
                "three wheat consumed across three fires"),
            () -> assertEquals(3, pass.nextBudget().get(FLOUR),
                "three fires of 1 flour credit the budget ledger")
        );
    }

    @Test
    @DisplayName("the recorded input totals match apply(StockLedger) round-trip arithmetic")
    void consumedTotalsMatchApplyRoundTrip() {
        // A multi-input rule (coal + stick -> torch). The helper drains
        // both inputs per fire and the consumed map must contain both
        // items with the per-fire amounts.
        TransformationRule torchRule = rule(
            List.of(
                new StockCost(COAL, 1),
                new StockCost(STICK, 1)),
            TORCH, 4, 256);
        StockLedger budget = StockLedger.EMPTY
            .add(COAL, 2)
            .add(STICK, 2);

        ProductionManager.TransformerBudgetPass pass = ProductionManager.runTransformerBudget(
            List.of(torchRule), budget, new int[]{10});

        assertAll(
            () -> assertEquals(2, pass.appliedPerRule().get(torchRule),
                "two fires — one coal and one stick per fire, two of each in the budget"),
            () -> assertEquals(2, pass.consumedPerItem().get(COAL),
                "two coal consumed across two fires"),
            () -> assertEquals(2, pass.consumedPerItem().get(STICK),
                "two sticks consumed across two fires"),
            () -> assertEquals(0, pass.nextBudget().get(COAL),
                "coal budget fully drained"),
            () -> assertEquals(0, pass.nextBudget().get(STICK),
                "stick budget fully drained"),
            () -> assertEquals(8, pass.nextBudget().get(TORCH),
                "two fires of 4 torches each land on the budget ledger")
        );
    }

    @Test
    @DisplayName("rules.size() must equal remainingFiresPerRule.length — mismatch is rejected up front")
    void sizeMismatchRejected() {
        TransformationRule r = rule(
            List.of(new StockCost(WHEAT, 1)), FLOUR, 1, 64);

        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> ProductionManager.runTransformerBudget(
                    List.of(r), StockLedger.EMPTY, new int[]{1, 2}),
                "remainingFiresPerRule longer than rules is rejected"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ProductionManager.runTransformerBudget(
                    List.of(r, r), StockLedger.EMPTY, new int[]{1}),
                "remainingFiresPerRule shorter than rules is rejected")
        );
    }
}
