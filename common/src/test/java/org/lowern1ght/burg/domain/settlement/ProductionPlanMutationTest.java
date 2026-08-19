package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link ProductionPlan}: due-output
 * arithmetic (scaling, rounding-to-nearest, sub-unit collapse to zero),
 * duplicate-output merging, and the irrelevance of {@code lastTick}.
 * Kills mutants like a {@code Math.round} becoming a cast, a
 * {@code scaled <= 0} guard losing its equals, or an output put that
 * overwrites instead of merging.
 */
class ProductionPlanMutationTest {

    private static final ItemId BREAD = ItemId.of("minecraft:bread");
    private static final ItemId ARROW = ItemId.of("minecraft:arrow");

    private static ProductionRule rule(ItemId output, int amount, long everyTicks) {
        return new ProductionRule(output, amount, everyTicks, 64);
    }

    @Test
    @DisplayName("EMPTY plan computes no outputs at any tick")
    void emptyPlanProducesNothing() {
        for (long t = 0; t < 5; t++) {
            assertEquals(Map.of(), ProductionPlan.EMPTY.computeDueOutputs(t, 0L),
                "tick " + t + " on an empty plan yields the empty map, not null");
        }
        assertTrue(ProductionPlan.EMPTY.isEmpty());
        assertEquals(1.0, ProductionPlan.EMPTY.bonusMultiplier());
    }

    @Test
    @DisplayName("a due rule emits amount × multiplier; a non-due rule emits nothing")
    void dueAndNotDue() {
        ProductionPlan plan = new ProductionPlan(List.of(rule(BREAD, 4, 10)), 2.0);

        assertAll(
            () -> assertEquals(Map.of(BREAD, 8), plan.computeDueOutputs(20, 0L),
                "due tick: 4 × 2.0 = 8"),
            () -> assertEquals(Map.of(), plan.computeDueOutputs(21, 0L),
                "off-cadence tick: nothing")
        );
    }

    @Test
    @DisplayName("bonus multiplier rounds to nearest: 3 × 1.5 = 4.5 → 5, 1 × 0.5 = 0.5 → 1")
    void scalingRoundsToNearest() {
        ProductionPlan halfAgain = new ProductionPlan(List.of(rule(BREAD, 3, 10)), 1.5);
        ProductionPlan half = new ProductionPlan(List.of(rule(BREAD, 1, 10)), 0.5);

        assertAll(
            () -> assertEquals(5, halfAgain.computeDueOutputs(10, 0L).get(BREAD),
                "Math.round(4.5) is 5 — half rounds up (kills a cast-to-int mutant → 4)"),
            () -> assertEquals(1, half.computeDueOutputs(10, 0L).get(BREAD),
                "Math.round(0.5) is 1")
        );
    }

    @Test
    @DisplayName("a scaled amount below one half collapses to zero and is skipped entirely")
    void subUnitScaledOutputCollapses() {
        ProductionPlan tiny = new ProductionPlan(List.of(rule(BREAD, 1, 10)), 0.4);

        assertEquals(Map.of(), tiny.computeDueOutputs(10, 0L),
            "Math.round(0.4) = 0 — a zero scaled amount never reaches the output map");
    }

    @Test
    @DisplayName("a zero bonus multiplier collapses the whole plan to no output even when due")
    void zeroMultiplierCollapses() {
        ProductionPlan halted = new ProductionPlan(List.of(rule(BREAD, 4, 10)), 0.0);

        assertEquals(Map.of(), halted.computeDueOutputs(10, 0L),
            "multiplier <= 0.0 gates every rule (kills a '< 0.0' mutant that would emit 0-keyed entries)");
    }

    @Test
    @DisplayName("two due rules with the same output merge into one summed entry")
    void duplicateOutputsSum() {
        ProductionPlan plan = new ProductionPlan(List.of(
            rule(BREAD, 2, 5),
            rule(BREAD, 3, 5)), 1.0);

        assertEquals(Map.of(BREAD, 5), plan.computeDueOutputs(10, 0L),
            "same-ItemId outputs merge with Integer::sum (kills an overwrite mutant → 3)");
    }

    @Test
    @DisplayName("mixed cadences: only the due rule appears in the output map")
    void mixedCadencesFilter() {
        ProductionPlan plan = new ProductionPlan(List.of(
            rule(BREAD, 2, 5),
            rule(ARROW, 7, 3)), 1.0);

        assertAll(
            () -> assertEquals(Map.of(BREAD, 2, ARROW, 7), plan.computeDueOutputs(15, 0L),
                "15 is a multiple of both 5 and 3"),
            () -> assertEquals(Map.of(ARROW, 7), plan.computeDueOutputs(3, 0L),
                "3 is a multiple of 3 only")
        );
    }

    @Test
    @DisplayName("lastTick is irrelevant today — the same gameTime yields the same outputs")
    void lastTickIsIrrelevant() {
        ProductionPlan plan = new ProductionPlan(List.of(rule(BREAD, 2, 5)), 1.0);

        Map<ItemId, Integer> withZero = plan.computeDueOutputs(10, 0L);
        Map<ItemId, Integer> withSelf = plan.computeDueOutputs(10, 10L);
        Map<ItemId, Integer> withGarbage = plan.computeDueOutputs(10, -12345L);

        assertAll(
            () -> assertEquals(withZero, withSelf),
            () -> assertEquals(withZero, withGarbage),
            () -> assertEquals(Map.of(BREAD, 2), withZero)
        );
    }

    @Test
    @DisplayName("computeDueOutputs is a pure function — same inputs, same map, receiver intact")
    void pureFunction() {
        ProductionPlan plan = new ProductionPlan(List.of(rule(BREAD, 2, 5)), 1.0);

        Map<ItemId, Integer> first = plan.computeDueOutputs(5, 0L);
        Map<ItemId, Integer> second = plan.computeDueOutputs(5, 0L);

        assertEquals(first, second, "repeated evaluation is stable");
        assertEquals(1, plan.rules().size(), "the plan itself is untouched by evaluation");
    }

    @Test
    @DisplayName("the rules list is defensively copied and exposed read-only")
    void rulesDefensivelyCopiedAndReadOnly() {
        List<ProductionRule> source = new ArrayList<>(List.of(rule(BREAD, 2, 5)));
        ProductionPlan plan = new ProductionPlan(source, 1.0);

        source.add(rule(ARROW, 1, 5));

        assertAll(
            () -> assertEquals(1, plan.rules().size(),
                "post-construction writes to the source do not leak in"),
            () -> assertThrows(UnsupportedOperationException.class,
                () -> plan.rules().add(rule(ARROW, 1, 5)),
                "rules() is unmodifiable")
        );
    }

    @Test
    @DisplayName("a negative bonus multiplier is rejected at construction")
    void negativeMultiplierRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProductionPlan(List.of(), -0.1));
    }

    @Test
    @DisplayName("a null rules list is rejected at construction")
    void nullRulesRejected() {
        assertThrows(NullPointerException.class, () -> new ProductionPlan(null, 1.0));
    }

    @Test
    @DisplayName("two plans built from equal inputs compute equal outputs — value equality where it matters")
    void plansCompareByOutputs() {
        ProductionPlan a = new ProductionPlan(List.of(rule(BREAD, 2, 5)), 1.0);
        ProductionPlan b = new ProductionPlan(List.of(rule(BREAD, 2, 5)), 1.0);
        ProductionPlan boosted = new ProductionPlan(List.of(rule(BREAD, 2, 5)), 2.0);

        assertAll(
            () -> assertEquals(a.computeDueOutputs(10, 0L), b.computeDueOutputs(10, 0L),
                "ProductionPlan is a plain class without equals — outputs are the contract"),
            () -> assertNotEquals(a.computeDueOutputs(10, 0L), boosted.computeDueOutputs(10, 0L),
                "the multiplier discriminates the computed outputs"),
            () -> assertTrue(a.bonusMultiplier() != boosted.bonusMultiplier())
        );
    }
}
