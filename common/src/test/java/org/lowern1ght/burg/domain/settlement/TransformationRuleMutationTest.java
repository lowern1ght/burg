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
 * Mutation-style invariants for {@link TransformationRule}, centred on
 * the {@code canApply}/{@code apply} parity: for single-line inputs the
 * two agree on every quantity, so a mutant in either dies.
 *
 * <p><b>KNOWN GAP (documented, not fixed here):</b> for a rule with
 * <em>duplicate lines for the same item</em>, {@code canApply} checks
 * each line individually against the full stock, while {@code apply}
 * drains sequentially — so stock that satisfies each line alone but not
 * their sum makes {@code canApply} return {@code true} while
 * {@code apply} throws {@code IllegalStateException}. Callers that can
 * face duplicate lines must pre-check against {@link #inputTotals()}
 * (the summed view), against which parity is restored. The gap test
 * below pins the current divergence so an accidental "fix" or a
 * regression in either direction is noticed.
 */
class TransformationRuleMutationTest {

    private static final ItemId WHEAT = ItemId.of("minecraft:wheat");
    private static final ItemId COAL = ItemId.of("minecraft:coal");
    private static final ItemId FLOUR = ItemId.of("minecraft:flour");
    private static final ItemId TORCH = ItemId.of("minecraft:torch");

    @Test
    @DisplayName("single-line parity sweep: canApply ⟺ apply succeeds, for every quantity")
    void singleLineParitySweep() {
        TransformationRule rule = new TransformationRule(
            List.of(new TransformationRule.StockCost(WHEAT, 3)), FLOUR, 1, 64);

        for (int heldLoop = 0; heldLoop <= 6; heldLoop++) {
            final int held = heldLoop;
            StockLedger stock = StockLedger.EMPTY.add(WHEAT, held);
            boolean expected = held >= 3;

            assertEquals(expected, rule.canApply(stock),
                "canApply with " + held + " wheat must be " + expected);

            if (expected) {
                StockLedger result = rule.apply(stock);
                assertAll(
                    () -> assertEquals(held - 3, result.get(WHEAT),
                        "input drained by exactly the cost"),
                    () -> assertEquals(1, result.get(FLOUR),
                        "output added by exactly the amount"),
                    () -> assertEquals(held, stock.get(WHEAT),
                        "the receiver ledger is untouched (immutability)")
                );
            } else {
                assertThrows(IllegalStateException.class, () -> rule.apply(stock),
                    "apply with " + held + " wheat (< 3) must fail fast");
                assertEquals(held, stock.get(WHEAT),
                    "a failed apply leaves the caller's ledger intact");
            }
        }
    }

    @Test
    @DisplayName("multi-input apply drains every input and adds the output in one transaction")
    void multiInputApply() {
        TransformationRule rule = new TransformationRule(
            List.of(
                new TransformationRule.StockCost(WHEAT, 2),
                new TransformationRule.StockCost(COAL, 1)),
            FLOUR, 1, 64);

        StockLedger stock = StockLedger.EMPTY.add(WHEAT, 5).add(COAL, 4);
        StockLedger result = rule.apply(stock);

        assertAll(
            () -> assertTrue(rule.canApply(stock)),
            () -> assertEquals(3, result.get(WHEAT)),
            () -> assertEquals(3, result.get(COAL)),
            () -> assertEquals(1, result.get(FLOUR))
        );
    }

    @Test
    @DisplayName("a shortfall on the SECOND input throws and the caller's ledger survives")
    void secondInputShortfallFailsClean() {
        TransformationRule rule = new TransformationRule(
            List.of(
                new TransformationRule.StockCost(WHEAT, 2),
                new TransformationRule.StockCost(COAL, 1)),
            FLOUR, 1, 64);

        StockLedger stock = StockLedger.EMPTY.add(WHEAT, 5); // no coal at all

        assertAll(
            () -> assertFalse(rule.canApply(stock)),
            () -> assertThrows(IllegalStateException.class, () -> rule.apply(stock),
                "the sequential drain fails when it reaches coal"),
            () -> assertEquals(5, stock.get(WHEAT),
                "the ledger the caller holds is never partially drained"),
            () -> assertEquals(0, stock.get(COAL))
        );
    }

    @Test
    @DisplayName("a rule with no inputs only adds the output")
    void emptyInputsJustAdd() {
        TransformationRule generator = new TransformationRule(List.of(), TORCH, 2, 64);

        StockLedger stock = StockLedger.EMPTY.add(WHEAT, 5);

        assertAll(
            () -> assertTrue(generator.canApply(stock),
                "a rule with no inputs is always applicable"),
            () -> assertEquals(Map.of(), generator.inputTotals()),
            () -> assertEquals(2, generator.apply(stock).get(TORCH)),
            () -> assertEquals(5, generator.apply(stock).get(WHEAT),
                "unrelated stock passes through untouched")
        );
    }

    @Test
    @DisplayName("KNOWN GAP: duplicate lines — canApply says yes while apply throws")
    void knownGapDuplicateLinesDiverge() {
        // 3 wheat + 3 wheat from one rule. 5 wheat satisfies each line
        // individually (5 >= 3 twice) but not their sum (5 < 6).
        TransformationRule duplicated = new TransformationRule(
            List.of(
                new TransformationRule.StockCost(WHEAT, 3),
                new TransformationRule.StockCost(WHEAT, 3)),
            FLOUR, 1, 64);

        StockLedger five = StockLedger.EMPTY.add(WHEAT, 5);

        assertAll(
            () -> assertTrue(duplicated.canApply(five),
                "GAP: canApply checks each line individually, so 5 wheat passes"),
            () -> assertThrows(IllegalStateException.class, () -> duplicated.apply(five),
                "GAP: apply drains sequentially, 5→2→fail")
        );

        // Parity is restored when the stock covers the aggregate…
        StockLedger six = StockLedger.EMPTY.add(WHEAT, 6);
        assertAll(
            () -> assertTrue(duplicated.canApply(six)),
            () -> assertEquals(0, duplicated.apply(six).get(WHEAT),
                "6 wheat drains both lines to zero"),
            () -> assertEquals(1, duplicated.apply(six).get(FLOUR))
        );

        // …and inputTotals() is the aggregate view callers should pre-check against.
        assertEquals(Map.of(WHEAT, 6), duplicated.inputTotals(),
            "inputTotals sums duplicate lines — the honest per-item cost");
    }

    @Test
    @DisplayName("inputTotals merges duplicate lines and keeps distinct items separate")
    void inputTotalsMerge() {
        TransformationRule rule = new TransformationRule(
            List.of(
                new TransformationRule.StockCost(WHEAT, 2),
                new TransformationRule.StockCost(WHEAT, 3),
                new TransformationRule.StockCost(COAL, 1)),
            FLOUR, 1, 64);

        assertEquals(
            Map.of(WHEAT, 5, COAL, 1),
            rule.inputTotals(),
            "duplicate wheat lines sum to 5; coal stays its own key");
    }

    @Test
    @DisplayName("the inputs list is defensively copied and exposed read-only")
    void inputsDefensivelyCopiedAndReadOnly() {
        List<TransformationRule.StockCost> source =
            new java.util.ArrayList<>(List.of(new TransformationRule.StockCost(WHEAT, 1)));
        TransformationRule rule = new TransformationRule(source, FLOUR, 1, 64);

        source.add(new TransformationRule.StockCost(COAL, 1));

        assertAll(
            () -> assertEquals(1, rule.inputs().size(),
                "post-construction writes to the source do not change the rule"),
            () -> assertThrows(UnsupportedOperationException.class,
                () -> rule.inputs().add(new TransformationRule.StockCost(COAL, 1)))
        );
    }

    @Test
    @DisplayName("StockCost and the rule itself reject malformed amounts at construction")
    void constructionValidation() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule.StockCost(WHEAT, 0)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule.StockCost(WHEAT, -2)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule(List.of(), FLOUR, 0, 64),
                "outputAmount must be positive"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new TransformationRule(List.of(), FLOUR, 1, -1),
                "negative capacity is rejected"),
            () -> assertThrows(NullPointerException.class,
                () -> new TransformationRule(List.of(), null, 1, 64)),
            () -> assertThrows(NullPointerException.class,
                () -> new TransformationRule(null, FLOUR, 1, 64))
        );
    }
}
