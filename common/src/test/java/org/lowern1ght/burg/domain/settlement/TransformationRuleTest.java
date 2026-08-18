package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure-arithmetic transformer carve, on a bare JVM.
 *
 * <p>The full multi-pass / budgeted transformer loop is still in
 * {@code ProductionManager.tickTransformer} (intentionally — that is
 * its own carve). What this test pins is the contract the
 * {@code TransformationRule} domain helper gives the future loop:
 * (1) the apply path takes inputs and adds the output as a single
 * transaction on a {@link StockLedger}, (2) insufficient input throws
 * the same {@link IllegalStateException} {@link StockLedger#take}
 * raises, and (3) {@link TransformationRule#canApply} is a non-mutating
 * pre-check.
 */
class TransformationRuleTest {

    private static ItemId item(String raw) {
        return ItemId.of(raw);
    }

    private static final ItemId WHEAT = item("minecraft:wheat");
    private static final ItemId FLOUR = item("minecraft:flour");
    private static final ItemId OAK_LOG = item("minecraft:oak_log");
    private static final ItemId STICK = item("minecraft:stick");
    private static final ItemId COAL = item("minecraft:coal");
    private static final ItemId TORCH = item("minecraft:torch");

    @Test
    @DisplayName("StockCost rejects zero / negative amounts at construction")
    void stockCostValidates() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule.StockCost(WHEAT, 0),
                "zero is not a valid input amount"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule.StockCost(WHEAT, -3),
                "negative is not a valid input amount")
        );
    }

    @Test
    @DisplayName("TransformationRule rejects zero / negative output amounts and negative capacity")
    void transformationRuleValidates() {
        TransformationRule.StockCost wheat = new TransformationRule.StockCost(WHEAT, 1);
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule(List.of(wheat), FLOUR, 0, 64),
                "outputAmount must be positive"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule(List.of(wheat), FLOUR, -1, 64),
                "outputAmount must be positive"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule(List.of(wheat), FLOUR, 1, -1),
                "outputCapacityItems must be non-negative")
        );
    }

    @Test
    @DisplayName("apply drains all inputs and adds the output in one ledger transaction")
    void applyDrainsAndAdds() {
        // 2 wheat -> 1 flour.
        TransformationRule rule = new TransformationRule(
            List.of(new TransformationRule.StockCost(WHEAT, 2)),
            FLOUR, 1, 64);

        StockLedger source = StockLedger.EMPTY.add(WHEAT, 5);
        StockLedger next = rule.apply(source);

        assertAll(
            () -> assertEquals(3, next.get(WHEAT),
                "two wheat were consumed"),
            () -> assertEquals(1, next.get(FLOUR),
                "one flour was added"),
            () -> assertEquals(5, source.get(WHEAT),
                "the source ledger is unchanged (immutability)"),
            () -> assertEquals(0, source.get(FLOUR))
        );
    }

    @Test
    @DisplayName("apply sums duplicate inputs (e.g. 2x2 wheat -> 1 flour)")
    void applySumsDuplicateInputs() {
        TransformationRule rule = new TransformationRule(
            List.of(
                new TransformationRule.StockCost(WHEAT, 2),
                new TransformationRule.StockCost(WHEAT, 2)),
            FLOUR, 1, 64);

        StockLedger source = StockLedger.EMPTY.add(WHEAT, 4);
        StockLedger next = rule.apply(source);

        assertAll(
            () -> assertEquals(0, next.get(WHEAT),
                "all four wheat were consumed"),
            () -> assertEquals(1, next.get(FLOUR))
        );
    }

    @Test
    @DisplayName("apply with multiple distinct inputs drains all of them in one transaction")
    void applyMultipleInputs() {
        // 1 coal + 1 stick -> 4 torches (vanilla recipe shape).
        TransformationRule rule = new TransformationRule(
            List.of(
                new TransformationRule.StockCost(COAL, 1),
                new TransformationRule.StockCost(STICK, 1)),
            TORCH, 4, 256);

        StockLedger source = StockLedger.EMPTY
            .add(COAL, 3)
            .add(STICK, 2);
        StockLedger next = rule.apply(source);

        assertAll(
            () -> assertEquals(2, next.get(COAL),
                "one coal was consumed"),
            () -> assertEquals(1, next.get(STICK),
                "one stick was consumed"),
            () -> assertEquals(4, next.get(TORCH),
                "four torches were added")
        );
    }

    @Test
    @DisplayName("apply throws IllegalStateException when any input is short — the source is unchanged")
    void applyFailsOnInsufficientInput() {
        TransformationRule rule = new TransformationRule(
            List.of(new TransformationRule.StockCost(WHEAT, 2)),
            FLOUR, 1, 64);

        // Only one wheat — apply must throw.
        StockLedger source = StockLedger.EMPTY.add(WHEAT, 1);
        assertThrows(IllegalStateException.class, () -> rule.apply(source),
            "demanding more wheat than is held throws");

        assertEquals(1, source.get(WHEAT),
            "a rejected apply leaves the source ledger intact (immutability)");
    }

    @Test
    @DisplayName("apply throws when the missing input is not on the ledger at all")
    void applyFailsOnMissingInput() {
        TransformationRule rule = new TransformationRule(
            List.of(new TransformationRule.StockCost(WHEAT, 1)),
            FLOUR, 1, 64);

        assertThrows(IllegalStateException.class,
            () -> rule.apply(StockLedger.EMPTY),
            "an input that is not on the ledger is read as zero — apply throws");
    }

    @Test
    @DisplayName("canApply is a non-mutating pre-check that matches the apply failure path")
    void canApply() {
        TransformationRule rule = new TransformationRule(
            List.of(new TransformationRule.StockCost(WHEAT, 2)),
            FLOUR, 1, 64);

        assertAll(
            () -> assertTrue(rule.canApply(StockLedger.EMPTY.add(WHEAT, 2)),
                "having exactly enough to fire is enough"),
            () -> assertTrue(rule.canApply(StockLedger.EMPTY.add(WHEAT, 5)),
                "having more than enough is enough"),
            () -> assertFalse(rule.canApply(StockLedger.EMPTY.add(WHEAT, 1)),
                "having less than the required amount is not enough"),
            () -> assertFalse(rule.canApply(StockLedger.EMPTY),
                "an empty ledger fails the pre-check")
        );
    }

    @Test
    @DisplayName("inputTotals sums duplicate inputs into a per-item total")
    void inputTotals() {
        TransformationRule rule = new TransformationRule(
            List.of(
                new TransformationRule.StockCost(WHEAT, 2),
                new TransformationRule.StockCost(COAL, 1),
                new TransformationRule.StockCost(WHEAT, 3)),
            FLOUR, 1, 64);

        Map<ItemId, Integer> totals = rule.inputTotals();
        assertEquals(5, totals.get(WHEAT),
            "duplicate WHEAT inputs sum to 5");
        assertEquals(1, totals.get(COAL));
        assertEquals(2, totals.size(),
            "the totals map has one entry per distinct input item");
    }

    @Test
    @DisplayName("the inputs list is defensively copied — mutating the source after construction does not change the rule")
    void inputsAreDefensivelyCopied() {
        java.util.List<TransformationRule.StockCost> source = new java.util.ArrayList<>();
        source.add(new TransformationRule.StockCost(WHEAT, 1));
        TransformationRule rule = new TransformationRule(
            source, FLOUR, 1, 64);

        // Mutate the source after construction.
        source.add(new TransformationRule.StockCost(COAL, 1));
        source.clear();

        assertEquals(1, rule.inputs().size(),
            "the rule's inputs are independent of the source list");
        assertEquals(WHEAT, rule.inputs().get(0).item());
    }

    @Test
    @DisplayName("inputs() is a read-only view of the rule's input list")
    void inputsViewIsReadOnly() {
        TransformationRule rule = new TransformationRule(
            List.of(new TransformationRule.StockCost(WHEAT, 1)),
            FLOUR, 1, 64);

        assertThrows(UnsupportedOperationException.class,
            () -> rule.inputs().add(new TransformationRule.StockCost(COAL, 1)),
            "inputs() returns an unmodifiable view");

        // Sanity: oak_log is not in the rule, but StockLedger.add tolerates
        // unrelated items, so a stray add does not change the test outcome
        // beyond proving the view is locked.
        assertEquals(1, rule.inputs().size());
    }
}
